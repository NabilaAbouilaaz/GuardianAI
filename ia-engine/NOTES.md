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

## À faire

**Explicabilité (SHAP).** Le moteur ne renvoie aujourd'hui qu'un score. La page Alerts de
l'interface affiche des facteurs d'explication codés en dur, qui décrivent des
comportements dynamiques que l'analyse statique ne peut pas observer. `shap.TreeExplainer`
s'interface nativement avec LightGBM et permettrait d'exposer les caractéristiques ayant
réellement pesé sur chaque verdict.
