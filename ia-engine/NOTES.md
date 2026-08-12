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
| `lightgbm_ember2024_v2.joblib` | **Modèle en service.** Référencé par `MODEL_PATH`. |
| `metrics_ember2024_v3.json` | **Métriques en service.** Seuil calibré sur un jeu de validation distinct. |
| `metrics_ember2024_v2.json` | Métriques initiales, conservées pour trace. Seuil calibré sur le jeu de test lui-même. |
| `lightgbm_ember2024_v1.joblib` | Itération précédente, conservée pour comparaison. |
| `xgboost_baseline_v1.joblib` | Référence initiale, écartée au profit de LightGBM. |

Le seuil de décision n'est **pas** codé en dur : il est lu depuis le fichier de métriques
au démarrage (`SEUIL_MALVEILLANT = float(metrics["seuil"])`). Réentraîner le modèle
implique donc de régénérer les deux fichiers ensemble.

### Pourquoi v3 et non v2

Le modèle est identique dans les deux cas ; seul le seuil change.

La version v2 sélectionnait le seuil sur le jeu de test, puis annonçait les performances
sur ce même jeu. Le taux de faux positifs valait donc 2,00 % **par construction**, et le
taux de détection de 97,27 % était légèrement optimiste.

La v3 coupe le jeu de test en deux : le seuil est calibré sur une moitié, les performances
mesurées sur l'autre. Résultat : seuil 0,6815, **1,90 % de faux positifs et 97,16 % de
détection** sur des données jamais utilisées pour la calibration.

L'écart est faible — 0,11 point — ce qui s'explique par la taille du jeu de test, 180 000
fichiers de chaque côté. Mais la seconde mesure permet d'affirmer que la contrainte des 2 %
tient hors du jeu de calibration, ce que la première ne permettait pas.

La cellule ayant produit ces chiffres est `CELLULE_COLAB_recalibration.py`.

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

## Consommation mémoire de l'extraction

`thrember` construit son histogramme d'octets avec `np.bincount`, qui promeut chaque
octet du fichier en entier 64 bits : **8 octets de mémoire par octet de fichier**, plus
les tampons intermédiaires.

Mesures réalisées avec `inspect_memoire.py` sur douze binaires Windows :

| Fichier | Taille | Pic mémoire | Ratio |
|---|---|---|---|
| AutoCatHost.exe | 1,6 Mo | 14,1 Mo | 9,0× |
| sppsvc.exe | 4,8 Mo | 62,0 Mo | 12,9× |
| ntoskrnl.exe | 13,0 Mo | 249,3 Mo | 19,1× |
| **igd10iumd64.dll** | **16,5 Mo** | **693,4 Mo** | **42,2×** |
| excelcnv.exe | 53,8 Mo | 982,0 Mo | 18,3× |

**Le ratio n'est pas constant** : il varie de 9× à 42× et dépend de la structure du
binaire — nombre de sections, de chaînes, d'imports — autant que de sa taille.
`igd10iumd64.dll` consomme davantage que des fichiers trois fois plus gros.

### Conséquence sur la limite annoncée

Le cahier des charges annonce 200 Mo par fichier (UC-01). Au pire ratio observé, cela
demanderait **8,4 Go de mémoire libre d'un seul tenant**. Sur la machine de
développement, avec 2 Go disponibles, la limite tenable est d'environ 48 Mo.

La limite de 200 Mo reste donc **conditionnée à l'environnement d'exécution**, ce qui
doit être documenté plutôt que masqué. Un fichier trop volumineux ne provoque plus une
erreur générique mais un **507 Insufficient Storage** accompagné d'une estimation du
besoin réel — l'utilisateur sait alors s'il doit libérer de la mémoire ou changer de
machine.

Aucun échec n'a été constaté jusqu'à 53,8 Mo dans les conditions du test.

## Le groupe `authenticode` sur les binaires Windows — question tranchée

**Constat initial.** Sur trois binaires système Microsoft (`notepad.exe`, `calc.exe`,
`wpnpinst.exe`), le groupe `authenticode` contribue toujours vers un verdict malveillant :
+0,35, +0,34 et +0,15. Contre-intuitif pour des fichiers signés par Microsoft.

**Hypothèse écartée.** On a d'abord soupçonné un échec silencieux de `signify`, qui aurait
laissé les huit dimensions à leur valeur par défaut. Vérification faite avec
`inspect_authenticode.py`, les valeurs brutes sont bien toutes nulles — mais avec
`parse_error: 0`. Or le code de `thrember` met ce drapeau à 1 dès qu'une exception survient :

```python
except signify.exceptions.ParseError:
    raw_obj["parse_error"] = 1
```

Aucune erreur, donc aucune signature trouvée. L'extraction n'a pas échoué.

**Explication réelle.** Microsoft ne signe pas ses binaires système en intégrant le
certificat dans le fichier. Elle utilise des **catalogues de sécurité** — des fichiers `.cat`
séparés qui référencent l'empreinte du binaire. Le fichier lui-même ne contient aucune
signature, et `num_certs: 0` est donc exact.

Vérification directe, en interrogeant `signify` sans passer par la vectorisation :

```
notepad.exe  → 0 signature intégrée
Code.exe     → 1 signature intégrée
```

**Conclusion.** L'extraction fonctionne, l'environnement local est cohérent avec celui de
l'entraînement, et les métriques ne sont pas affectées. La contribution positive traduit un
fait : le modèle a appris que l'absence de signature intégrée est plus fréquente chez les
malwares que chez les logiciels commercialement distribués. Sur un binaire système signé par
catalogue, ce raisonnement est mis en défaut — mais le verdict global reste correct, les
autres groupes compensant largement.

C'est une limite intéressante à mentionner : le modèle ne distingue pas « non signé » de
« signé par catalogue ».
