"""
GuardianAI — Moteur IA d'analyse de fichiers
Microservice FastAPI exposant le modele de detection de malwares.

Modele  : LightGBM entraine sur EMBER2024 (.NET + Win64), features version 3 (thrember).
Seuil   : calibre pour garantir un taux de faux positifs <= 2% (RNF-03), et non 0.5.
Perf    : extracteur instancie une seule fois + cache par empreinte SHA-256 (RNF-01).
"""

import hashlib
import json
import os
import time
from collections import OrderedDict

import joblib
import numpy as np
import shap
import thrember
from fastapi import FastAPI, File, HTTPException, UploadFile

BASE_DIR = os.path.dirname(__file__)
MODEL_PATH = os.path.join(BASE_DIR, "models", "lightgbm_ember2024_v2.joblib")

# Metriques v3 et non v2 : le modele est le meme, seul le seuil change.
#
# La version v2 choisissait le seuil sur le jeu de test, puis annoncait les
# performances sur ce meme jeu — le taux de faux positifs valait donc 2,00 % par
# construction. La v3 calibre sur une moitie du jeu de test et mesure sur
# l'autre, restee vierge : 0,6815 au lieu de 0,6638.
#
# Sur des donnees jamais vues, ce seuil donne 1,90 % de faux positifs et 97,16 %
# de detection. La contrainte des 2 % (RNF-03) tient donc hors du jeu ayant servi
# a la calibration, ce que l'ancienne mesure ne permettait pas d'affirmer.
METRICS_PATH = os.path.join(BASE_DIR, "models", "metrics_ember2024_v3.json")

MODEL_VERSION = "lightgbm_ember2024_v2"
MAX_FILE_SIZE = 200 * 1024 * 1024  # 200 Mo (UC-01)
SEUIL_SUSPECT = 0.5
CACHE_MAX = 5000  # nombre d'empreintes conservees en memoire

# Rapport le plus defavorable entre memoire consommee et taille du fichier,
# mesure sur douze binaires Windows de 0,9 a 53,8 Mo avec inspect_memoire.py.
# Sert uniquement a formuler un message d'erreur exploitable, pas a decider.
RATIO_MEMOIRE_MAX = 42.0

# Decoupage du vecteur de caracteristiques par groupe.
#
# Le vecteur produit par thrember (2568 dimensions) est la concatenation de
# plusieurs extracteurs. Les valeurs SHAP sont calculees par dimension, ce qui
# n'est pas exploitable tel quel : "caracteristique 1847" ne parle a personne.
# On les agrege donc par groupe, qui a un sens pour un analyste.
#
# Bornes relevees avec inspect_features.py sur la version installee de thrember,
# et non supposees : un decalage d'un seul indice attribuerait silencieusement une
# contribution au mauvais groupe. Relancer ce script apres toute mise a jour.
GROUPES = {
    "general":         (0, 7,       "Informations generales"),
    "histogram":       (7, 263,     "Histogramme d'octets"),
    "byteentropy":     (263, 519,   "Entropie des octets"),
    "strings":         (519, 696,   "Chaines de caracteres"),
    "header":          (696, 770,   "En-tetes PE"),
    "section":         (770, 994,   "Sections"),
    "imports":         (994, 2276,  "Fonctions importees"),
    "exports":         (2276, 2405, "Fonctions exportees"),
    "datadirectories": (2405, 2439, "Repertoires de donnees"),
    "richheader":      (2439, 2472, "En-tete Rich"),
    "authenticode":    (2472, 2480, "Signature numerique"),
    "pefilewarnings":  (2480, 2568, "Anomalies de structure PE"),
}

app = FastAPI(
    title="GuardianAI — Moteur IA",
    description="Analyse de fichiers et classification benin / suspect / malveillant.",
    version="2.1.0",
)

# --- Chargements uniques au demarrage.
# L'extracteur est couteux a construire : le recreer a chaque requete
# dominait le temps de reponse (mesure : ~2,5 s par fichier).
model = joblib.load(MODEL_PATH)
extractor = thrember.PEFeatureExtractor()

# TreeExplainer exploite la structure des arbres pour calculer les valeurs de
# Shapley exactes en temps polynomial, la ou le calcul generique serait
# exponentiel. Construit une seule fois : le reconstruire par requete couterait
# plusieurs secondes.
explainer = shap.TreeExplainer(model)

with open(METRICS_PATH, encoding="utf-8") as f:
    metrics = json.load(f)

SEUIL_MALVEILLANT = float(metrics["seuil"])

# Cache LRU simple : meme fichier soumis deux fois = reponse immediate.
_cache: "OrderedDict[str, dict]" = OrderedDict()


@app.get("/health")
def health():
    """Verification de disponibilite du service."""
    return {"status": "ok", "model_version": MODEL_VERSION}


@app.get("/model-info")
def model_info():
    """Caracteristiques et performances mesurees du modele en service."""
    return {
        "model_version": MODEL_VERSION,
        "dataset": metrics.get("dataset", "EMBER2024 (.NET + Win64)"),
        "feature_version": "EMBER v3 (thrember / pefile)",
        "seuil_malveillant": SEUIL_MALVEILLANT,
        "seuil_suspect": SEUIL_SUSPECT,
        "performances": {
            "roc_auc": metrics.get("roc_auc"),
            "taux_faux_positifs": metrics.get("faux_positifs"),
            "taux_detection": metrics.get("detection"),
            "detection_malwares_evasifs": metrics.get("detection_challenge"),
        },
        "cache": {"entrees": len(_cache), "capacite": CACHE_MAX},
    }


def _classer(score: float) -> str:
    """Traduit un score de malveillance en verdict a trois niveaux.

    Le seuil haut est calibre sur le jeu de test pour tenir la contrainte
    de 2% de faux positifs. La zone intermediaire est signalee comme
    suspecte plutot que tranchee arbitrairement.
    """
    if score >= SEUIL_MALVEILLANT:
        return "malveillant"
    if score >= SEUIL_SUSPECT:
        return "suspect"
    return "benin"


def _message_memoire(taille_octets: int) -> str:
    """Explique un echec memoire en termes actionnables.

    L'extraction consomme de 9 a 42 fois la taille du fichier, mesure sur des
    binaires Windows de 0,9 a 53,8 Mo (voir inspect_memoire.py). L'origine est
    dans thrember : np.bincount promeut chaque octet en entier 64 bits pour
    construire l'histogramme, soit 8 octets de memoire par octet de fichier,
    auxquels s'ajoutent les tampons intermediaires.

    Le ratio depend de la structure du binaire autant que de sa taille, d'ou une
    estimation prudente fondee sur le pire cas observe.
    """
    taille_mo = taille_octets / 1e6
    besoin_go = taille_mo * RATIO_MEMOIRE_MAX / 1000

    return (
        f"Memoire insuffisante pour analyser ce fichier de {taille_mo:.0f} Mo. "
        f"L'extraction des caracteristiques peut demander jusqu'a "
        f"{RATIO_MEMOIRE_MAX:.0f} fois la taille du fichier, soit environ "
        f"{besoin_go:.1f} Go pour celui-ci. Liberer de la memoire, ou analyser "
        f"ce fichier sur une machine mieux dotee."
    )


def _contributions(vecteur: np.ndarray) -> dict:
    """Repartit la decision du modele entre les groupes de caracteristiques.

    Les valeurs de Shapley sont calculees dans l'espace des log-odds, celui ou
    LightGBM additionne les sorties de ses arbres. La somme des contributions
    ajoutee a la valeur de base redonne donc la marge brute, dont la sigmoide
    redonne la probabilite. Les deux quantites sont exposees pour permettre de
    verifier cette egalite : une explication qui ne se recompose pas est fausse.
    """
    valeurs = explainer.shap_values(vecteur)

    # Selon la version de shap et le type de modele, la sortie est soit un
    # tableau, soit une liste d'un tableau par classe. On se ramene au vecteur
    # de la classe positive.
    if isinstance(valeurs, list):
        valeurs = valeurs[-1]
    valeurs = np.asarray(valeurs, dtype=np.float64).reshape(-1)

    base = explainer.expected_value
    if isinstance(base, (list, np.ndarray)):
        base = float(np.asarray(base).reshape(-1)[-1])
    else:
        base = float(base)

    contributions = []
    for debut, fin, libelle in GROUPES.values():
        total = float(valeurs[debut:fin].sum())
        contributions.append({
            "groupe": libelle,
            "valeur": round(total, 4),
            "direction": "malveillant" if total > 0 else "benin",
        })

    # Tri par poids decroissant : l'analyste doit voir en premier ce qui a le
    # plus pese, quel que soit le sens.
    contributions.sort(key=lambda c: abs(c["valeur"]), reverse=True)

    somme = float(valeurs.sum())
    marge = base + somme

    return {
        "contributions": contributions,
        "valeur_de_base": round(base, 4),
        "somme_contributions": round(somme, 4),
        # Reconstruction du score a partir de la seule explication : doit
        # coincider avec score_malveillance a l'arrondi pres.
        "score_reconstruit": round(float(1.0 / (1.0 + np.exp(-marge))) * 100, 2),
    }


def _mettre_en_cache(empreinte: str, resultat: dict) -> None:
    _cache[empreinte] = resultat
    _cache.move_to_end(empreinte)
    while len(_cache) > CACHE_MAX:
        _cache.popitem(last=False)


@app.post("/predict")
async def predict(file: UploadFile = File(...), expliquer: bool = False):
    """Analyse un fichier et retourne un verdict avec son score de confiance.

    Le parametre `expliquer` ajoute la decomposition SHAP a la reponse. Il existe
    pour eviter au client d'enchainer /predict puis /explain, ce qui extrairait
    deux fois les caracteristiques du meme fichier : l'extraction domine le temps
    de reponse, la recalculer serait le double du cout utile.
    """
    debut = time.perf_counter()
    contents = await file.read()

    if len(contents) == 0:
        raise HTTPException(status_code=400, detail="Fichier vide.")
    if len(contents) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=413,
            detail="Fichier trop volumineux (limite : 200 Mo). "
                   "Utiliser l'analyse asynchrone.",
        )

    empreinte = hashlib.sha256(contents).hexdigest()

    # Le cache n'est utilisable que s'il contient deja ce que le client demande :
    # une entree enregistree sans explication ne peut pas en fournir une, le
    # vecteur n'ayant pas ete conserve.
    en_cache = _cache.get(empreinte)
    if en_cache is not None and (not expliquer or "contributions" in en_cache):
        resultat = dict(en_cache)
        _cache.move_to_end(empreinte)
        if not expliquer:
            resultat.pop("contributions", None)
            resultat.pop("valeur_de_base", None)
            resultat.pop("somme_contributions", None)
            resultat.pop("score_reconstruit", None)
        resultat["filename"] = file.filename
        resultat["cache"] = True
        resultat["duree_ms"] = round((time.perf_counter() - debut) * 1000, 1)
        return resultat

    try:
        vecteur = np.asarray(extractor.feature_vector(contents),
                             dtype=np.float32).reshape(1, -1)
        score = float(model.predict(vecteur)[0])
        explication = _contributions(vecteur) if expliquer else {}

    # La memoire est traitee a part : ce n'est pas le fichier qui est en cause,
    # et le message doit dire quoi faire plutot que de constater un echec.
    except MemoryError as exc:
        raise HTTPException(status_code=507, detail=_message_memoire(len(contents))) from exc

    except Exception as exc:  # fichier illisible, corrompu ou format non gere
        raise HTTPException(
            status_code=422,
            detail=f"Analyse impossible : {type(exc).__name__} — {exc}",
        ) from exc

    resultat = {
        "filename": file.filename,
        "sha256": empreinte,
        "taille_octets": len(contents),
        "classification": _classer(score),
        "score_malveillance": round(score * 100, 2),
        "seuil_applique": round(SEUIL_MALVEILLANT * 100, 2),
        "model_version": MODEL_VERSION,
        "cache": False,
        **explication,
    }

    _mettre_en_cache(empreinte, {k: v for k, v in resultat.items()
                                 if k not in ("filename", "cache")})

    resultat["duree_ms"] = round((time.perf_counter() - debut) * 1000, 1)
    return resultat


@app.post("/explain")
async def explain(file: UploadFile = File(...)):
    """Explique le verdict rendu sur un fichier, groupe par groupe.

    Endpoint distinct de /predict et non extension de celui-ci : l'explication
    n'est utile que lorsqu'un analyste la demande, et l'imposer a chaque analyse
    penaliserait le cas courant sans benefice.
    """
    debut = time.perf_counter()
    contents = await file.read()

    if len(contents) == 0:
        raise HTTPException(status_code=400, detail="Fichier vide.")
    if len(contents) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=413,
            detail="Fichier trop volumineux (limite : 200 Mo).",
        )

    empreinte = hashlib.sha256(contents).hexdigest()

    try:
        vecteur = np.asarray(extractor.feature_vector(contents),
                             dtype=np.float32).reshape(1, -1)
        score = float(model.predict(vecteur)[0])
        explication = _contributions(vecteur)

    except MemoryError as exc:
        raise HTTPException(status_code=507, detail=_message_memoire(len(contents))) from exc

    except Exception as exc:
        raise HTTPException(
            status_code=422,
            detail=f"Explication impossible : {type(exc).__name__} — {exc}",
        ) from exc

    return {
        "filename": file.filename,
        "sha256": empreinte,
        "classification": _classer(score),
        "score_malveillance": round(score * 100, 2),
        "model_version": MODEL_VERSION,
        **explication,
        "duree_ms": round((time.perf_counter() - debut) * 1000, 1),
    }