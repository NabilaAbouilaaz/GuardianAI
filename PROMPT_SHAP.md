# Prompt — Implémenter l'explicabilité SHAP

Copier le contenu ci-dessous dans Claude Code, ouvert à la racine du projet GuardianAI.

---

Lis d'abord `BRIEF_CORRECTIONS.md` et `ia-engine/NOTES.md` pour le contexte, puis
implémente l'explicabilité SHAP de bout en bout.

## Objectif

Aujourd'hui, la page Alerts affiche des facteurs d'explication codés en dur dans
`frontend/src/app/features/alerts/alerts.component.ts` :

```typescript
readonly shapFeatures: ShapFeature[] = [
  { feature: 'Mass file encryption loop', score: 0.94, direction: 'malicious' },
  { feature: 'C2 beacon pattern (TLS 1.2)', score: 0.71, direction: 'malicious' },
];
```

Ces libellés décrivent des comportements dynamiques (chiffrement en masse, communication
vers un serveur de commande). Le moteur réalise une analyse **strictement statique** et
ne peut structurellement pas les observer. Il faut les remplacer par des explications
réellement calculées.

## Contrainte de conception essentielle

Le vecteur de caractéristiques EMBER2024 compte environ 2400 dimensions sans nom lisible.
Renvoyer « caractéristique 1847 : 0.23 » n'explique rien.

**Agréger les valeurs SHAP par groupe de caractéristiques EMBER**, qui ont un sens
métier : histogramme d'octets, histogramme d'entropie, chaînes de caractères,
informations générales du fichier, en-têtes PE, sections, imports, exports, répertoires
de données.

Les bornes d'indices de chaque groupe sont déterminées à partir de l'ordre de
concaténation dans `thrember.PEFeatureExtractor`. Vérifier cet ordre dans le code de la
bibliothèque installée plutôt que de le supposer, et documenter le mapping obtenu dans
`ia-engine/NOTES.md`.

## 1. Moteur — `ia-engine/main.py`

- Ajouter `shap` à `requirements.txt`, avec une version bornée à la majeure.
- Construire un `shap.TreeExplainer` **une seule fois au démarrage**, à côté du
  chargement du modèle et de l'extracteur. Le reconstruire à chaque requête serait
  incompatible avec l'exigence RNF-01 (moins de 3 secondes).
- Exposer un nouvel endpoint `POST /explain` qui prend un fichier et renvoie les
  contributions agrégées par groupe, triées par valeur absolue décroissante :

```json
{
  "sha256": "...",
  "model_version": "lightgbm_ember2024_v2",
  "score_malveillance": 0.04,
  "contributions": [
    {"groupe": "En-tetes PE", "valeur": -0.31, "direction": "benin"},
    {"groupe": "Imports", "valeur": 0.18, "direction": "malveillant"}
  ],
  "duree_ms": 12.4
}
```

- `direction` vaut `"malveillant"` si la contribution pousse le score vers le haut,
  `"benin"` sinon.
- Réutiliser le cache LRU existant, indexé par SHA-256, selon le même schéma que
  `/predict`.
- Mesurer le surcoût et le consigner : si le calcul SHAP dépasse quelques dizaines de
  millisecondes, l'intégrer plutôt dans `/predict` serait un mauvais choix.

Ne pas modifier le comportement de `/predict`, `/health` ni `/model-info`.

## 2. Backend — `backend/src/main/java/com/guardianai/backend/`

- Créer un record DTO pour les contributions, sur le modèle de `IaVerdict` : noms de
  champs Python mappés explicitement avec `@JsonProperty`.
- Ajouter une méthode `explain(MultipartFile)` dans `IaEngineClient`, en réutilisant
  **exactement** la même construction de requête que `analyze()` : `MultiValueMap` et
  `SimpleClientHttpRequestFactory`. Ne pas revenir à `MultipartBodyBuilder` ni à
  `JdkClientHttpRequestFactory` — les raisons sont documentées dans le code.
- Exposer `POST /api/v1/explain` dans `ScanController`, avec la même traduction des
  erreurs métier en 422 et 503.

## 3. Frontend

- Adapter l'interface `ShapFeature` dans `core/models/guardian.models.ts` au contrat
  réel renvoyé par l'API.
- Ajouter la méthode correspondante dans `core/services/guardian-data.service.ts`.
- Dans `features/alerts/alerts.component.ts` : supprimer `shapFeatures` codé en dur et
  charger les contributions de l'alerte sélectionnée. Suivre le motif déjà en place dans
  ce fichier : `subscribe({ next, error })`, `ChangeDetectorRef.detectChanges()` après
  chaque réponse, et un message d'erreur affiché.
- Le tableau `remediation`, également codé en dur, décrit des actions de remédiation
  génériques qu'aucun composant ne calcule. Le supprimer, ou le conserver en l'annonçant
  explicitement dans l'interface comme une liste de recommandations standard et non comme
  un résultat d'analyse.

## Difficulté connue

Les alertes exposées par `/api/v1/alerts` sont dérivées des analyses stockées en base,
qui ne conservent **pas** le fichier d'origine — seulement son empreinte. Il est donc
impossible de recalculer SHAP a posteriori à partir d'une alerte.

Deux options, à trancher et à justifier :

- Calculer et **stocker** les contributions au moment de l'analyse, dans une nouvelle
  colonne de `scan_result` ou une table liée. Nécessite une migration Flyway `V2__`.
- Ne proposer l'explication que **juste après un scan**, tant que le fichier est encore
  côté client.

La première est plus cohérente avec l'exigence de traçabilité RF-11, qui veut qu'une
décision reste justifiable a posteriori.

## Règles générales

- Ne pas modifier la configuration de la base, le port 5432, ni réintroduire Docker.
- Commenter les décisions non évidentes en expliquant **pourquoi**, dans le style déjà
  présent dans le projet.
- Ajouter des tests JUnit sur la logique de mapping côté backend.
- Mettre à jour `README.md` et `ia-engine/NOTES.md` en conséquence.
