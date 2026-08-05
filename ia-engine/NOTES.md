# Notes techniques — ia-engine

## Extraction de caractéristiques : `thrember`, et non `ember`

Le moteur utilise **`thrember`**, la bibliothèque publiée avec le jeu de données
EMBER2024 par FutureComputing4AI. Elle fournit `PEFeatureExtractor`, qui convertit un
exécutable Windows en vecteur de caractéristiques statiques (version 3 du jeu de
caractéristiques).

```python
import thrember
extractor = thrember.PEFeatureExtractor()
```

Installation, incluse dans `requirements.txt` :

```
git+https://github.com/FutureComputing4AI/EMBER2024.git
```

**Ne pas confondre avec `ember`**, le paquet historique d'Elastic. Ce dernier a été
envisagé au début du projet puis écarté : il est écrit pour `lief` 0.9–0.11, dont l'API
a changé depuis, et exigeait de patcher manuellement le code installé dans le
`site-packages` après chaque réinstallation. `thrember` est maintenu, compatible avec
les versions récentes de ses dépendances, et surtout aligné sur le jeu de données qui a
servi à l'entraînement — ce qui garantit que les caractéristiques calculées à
l'inférence sont identiques à celles vues pendant l'apprentissage.

## Version de `signify` figée

`signify` est épinglé à **0.7.1** dans `requirements.txt`, sans borne supérieure souple.
Les versions ultérieures ont modifié l'API de vérification des signatures Authenticode
utilisée par `thrember`, ce qui provoque une erreur à l'extraction. C'est le seul paquet
dont la version est strictement imposée.

## Modèles disponibles

`models/` contient plusieurs artefacts, seul le premier est en service :

| Fichier | Statut |
|---|---|
| `lightgbm_ember2024_v2.joblib` | **En service.** Référencé par `MODEL_PATH` dans `main.py`. |
| `metrics_ember2024_v2.json` | Métriques et seuil calibré du modèle en service. |
| `lightgbm_ember2024_v1.joblib` | Itération précédente, conservée pour comparaison. |
| `xgboost_baseline_v1.joblib` | Référence initiale, écartée au profit de LightGBM. |

Le seuil de décision n'est **pas** codé en dur : il est lu depuis le fichier de métriques
au démarrage (`SEUIL_MALVEILLANT = float(metrics["seuil"])`). Réentraîner le modèle
implique donc de régénérer les deux fichiers ensemble.

## Choix de conception à ne pas défaire

**L'extracteur est instancié une seule fois au démarrage.** Le construire à chaque
requête dominait le temps de réponse — environ 2,5 secondes par fichier mesurées, contre
une exigence à moins de 3 secondes (RNF-01).

**Un cache LRU indexé par empreinte SHA-256** évite de réanalyser un fichier déjà vu.
Capacité fixée à 5000 entrées.

**Le seuil de classification est calibré sur une contrainte de faux positifs**, pas fixé
à 0,5. Un outil qui signale trop souvent à tort finit ignoré des analystes ; le plafond
retenu est de 2 % (RNF-03). Une zone intermédiaire est classée « suspect » plutôt que
tranchée arbitrairement.

## Explicabilité (SHAP)

Le moteur décompose chaque verdict en contributions par groupe de caractéristiques.
`shap.TreeExplainer` exploite la structure des arbres de LightGBM pour calculer les
valeurs de Shapley exactes en temps polynomial ; il est construit une seule fois au
démarrage, pour la même raison que l'extracteur.

### Espace de calcul

**Les valeurs sont en log-odds, pas en probabilité.** C'est l'espace dans lequel LightGBM
additionne les sorties de ses arbres, et donc le seul où les contributions s'additionnent.

```
valeur_de_base + somme_contributions = marge brute
sigmoïde(marge brute)                = score_malveillance
```

La réponse expose `score_reconstruit`, qui applique cette égalité. **Il doit coïncider avec
`score_malveillance`** : une explication qui ne se recompose pas est fausse et ne doit pas
être utilisée. Ce contrôle est le premier à vérifier après toute modification du modèle.

### Agrégation par groupe

Le vecteur compte 2568 dimensions sans nom lisible ; renvoyer « caractéristique 1847 »
n'explique rien. Les valeurs sont donc agrégées selon le découpage relevé par
`inspect_features.py` :

| Groupe | Indices | Libellé affiché |
|---|---|---|
| general | 0–6 | Informations generales |
| histogram | 7–262 | Histogramme d'octets |
| byteentropy | 263–518 | Entropie des octets |
| strings | 519–695 | Chaines de caracteres |
| header | 696–769 | En-tetes PE |
| section | 770–993 | Sections |
| imports | 994–2275 | Fonctions importees |
| exports | 2276–2404 | Fonctions exportees |
| datadirectories | 2405–2438 | Repertoires de donnees |
| richheader | 2439–2471 | En-tete Rich |
| authenticode | 2472–2479 | Signature numerique |
| pefilewarnings | 2480–2567 | Anomalies de structure PE |

**Relancer `inspect_features.py` après toute mise à jour de `thrember`.** Un décalage d'un
seul indice attribuerait silencieusement une contribution au mauvais groupe, produisant
des explications fausses mais crédibles.

### Points d'entrée

`POST /predict?expliquer=true` joint la décomposition à l'analyse, en réutilisant le
vecteur déjà extrait. `POST /explain` fait la même chose isolément, pour l'usage ponctuel.
Le premier est utilisé par le backend : enchaîner les deux appels extrairait deux fois les
caractéristiques du même fichier, ce qui doublerait le coût dominant.

Le cache LRU ne sert une entrée que si elle contient déjà ce qui est demandé : une analyse
mise en cache sans explication ne peut pas en fournir une, le vecteur n'étant pas conservé.

## Observation à creuser

Sur trois binaires système Microsoft signés (`notepad.exe`, `calc.exe`, `wpnpinst.exe`), le
groupe `authenticode` contribue **toujours positivement**, c'est-à-dire vers un verdict
malveillant : +0,35, +0,34 et +0,15.

Une piste plausible : si `signify` échoue à analyser ces signatures, les caractéristiques du
groupe resteraient à leur valeur par défaut, que le modèle associerait à un binaire non
signé. À vérifier en inspectant directement les valeurs extraites sur ces indices. Trois
fichiers restent un échantillon trop faible pour conclure, mais la régularité justifie
l'investigation.
