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
import thrember
from fastapi import FastAPI, File, HTTPException, UploadFile

BASE_DIR = os.path.dirname(__file__)
MODEL_PATH = os.path.join(BASE_DIR, "models", "lightgbm_ember2024_v2.joblib")
METRICS_PATH = os.path.join(BASE_DIR, "models", "metrics_ember2024_v2.json")

MODEL_VERSION = "lightgbm_ember2024_v2"
MAX_FILE_SIZE = 200 * 1024 * 1024  # 200 Mo (UC-01)
SEUIL_SUSPECT = 0.5
CACHE_MAX = 5000  # nombre d'empreintes conservees en memoire

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


def _mettre_en_cache(empreinte: str, resultat: dict) -> None:
    _cache[empreinte] = resultat
    _cache.move_to_end(empreinte)
    while len(_cache) > CACHE_MAX:
        _cache.popitem(last=False)


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    """Analyse un fichier et retourne un verdict avec son score de confiance."""
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

    if empreinte in _cache:
        resultat = dict(_cache[empreinte])
        _cache.move_to_end(empreinte)
        resultat["filename"] = file.filename
        resultat["cache"] = True
        resultat["duree_ms"] = round((time.perf_counter() - debut) * 1000, 1)
        return resultat

    try:
        vecteur = np.asarray(extractor.feature_vector(contents),
                             dtype=np.float32).reshape(1, -1)
        score = float(model.predict(vecteur)[0])
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
    }

    _mettre_en_cache(empreinte, {k: v for k, v in resultat.items()
                                 if k not in ("filename", "cache")})

    resultat["duree_ms"] = round((time.perf_counter() - debut) * 1000, 1)
    return resultat