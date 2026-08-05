# Brief de corrections — GuardianAI

Document destiné à un assistant de développement. Contexte, état vérifié, et anomalies
restantes avec leurs preuves.

## Contexte technique

- **backend/** — Spring Boot 3 (Spring 7.0.8), Java 25, JPA + Flyway, PostgreSQL 18.2 local port 5432
- **ia-engine/** — FastAPI + uvicorn port 8000, modèle LightGBM sur EMBER2024 (`thrember`)
- **frontend/** — Angular standalone, Tailwind, port 4200

Les trois services tournent séparément. Aucun conteneur : Docker a été retiré du projet.

## Ce qui est vérifié et fonctionnel

La chaîne complète a été validée le 31/07/2026 :

```
curl.exe -X POST -F "file=@C:\Windows\System32\notepad.exe" http://localhost:8080/api/v1/scan
{"id":"SCN-B78CB9","filename":"notepad.exe","hash":"84b484fd3636f2ca","status":"CLEAN",
 "confidence":99.96,"analyst":"Moteur IA","timestamp":"2026-07-31 07:53:45",
 "size":"352 KB","type":".exe"}
```

Ne pas remettre en cause : le moteur IA, l'API REST, la persistance, les migrations Flyway.

## Anomalie 1 — La vue ne se rafraîchit pas (priorité haute, cause à confirmer)

**Symptôme reproductible.** Sur `/scan`, cliquer le bouton SCAN d'un fichier : la ligne
reste indéfiniment sur « ANALYSE EN COURS ». Cliquer ensuite SCAN ALL FILES : le verdict
CLEAN du **premier** scan s'affiche. Le dashboard reste vide alors que la base contient
des enregistrements.

**Analyse.** `scanFile()` et `scanAll()` appellent le même code ; il n'y a pas de
différence de traitement. Le résultat arrive donc bien, mais le rendu n'est déclenché
qu'au clic suivant. C'est un défaut de détection de changement, pas un problème réseau —
les requêtes retournent 200 et les données sont en base.

**Cause probable non confirmée.** La console du navigateur montre plusieurs extensions
Chrome injectant du code (`polyfill.js`, `injectScript.js`, `sidebar.js`). Une extension
qui remplace `XMLHttpRequest` empêche zone.js d'intercepter la fin des requêtes.

**Test décisif à faire avant toute modification.** Ouvrir `http://localhost:4200` en
navigation privée (extensions désactivées). Si tout fonctionne, la cause est confirmée.

**Correction attendue dans les deux cas.** L'application ne doit pas dépendre de
l'environnement du navigateur. Rendre le rafraîchissement explicite dans les composants
qui consomment `GuardianDataService` : `scan`, `dashboard`, `alerts`, `system-status`.

**Défaut connexe à corriger au passage.** Les `subscribe()` de `dashboard`, `alerts` et
`system-status` n'ont aucun gestionnaire d'erreur. Une panne du backend produit
exactement le même écran qu'une base vide, ce qui rend le diagnostic impossible.

## Anomalie 2 — Données fabriquées dans la page Alerts

`frontend/src/app/features/alerts/alerts.component.ts` :

```typescript
readonly shapFeatures: ShapFeature[] = [
  { feature: 'Mass file encryption loop', score: 0.94, direction: 'malicious' },
  { feature: 'C2 beacon pattern (TLS 1.2)', score: 0.71, direction: 'malicious' },
];
readonly remediation: string[] = [ /* recommandations codées en dur */ ];
```

Ces indicateurs décrivent des comportements dynamiques (chiffrement en masse,
communication vers un serveur de commande). Le moteur réalise une analyse **statique**
et ne peut pas les observer. La liste des alertes, elle, provient bien de l'API.

Deux options : masquer le panneau, ou exposer un vrai calcul SHAP côté moteur
(`shap.TreeExplainer` s'interface nativement avec LightGBM) et le consommer via un
nouvel endpoint. La seconde est préférable sur le fond mais demande un aller-retour
backend + moteur.

## Anomalie 3 — Compteurs du dashboard incohérents avec leur libellé

`frontend/src/app/features/dashboard/dashboard.component.ts` :

```typescript
get totalMalicious(): number {
  return this.recentScans.filter((s) => s.status === 'MALICIOUS').length;
}
```

Les cartes affichent « MALICIOUS (7D) », « SUSPICIOUS (7D) », « CLEAN (7D) », mais
`recentScans` renvoie les **20 dernières analyses**, pas les sept derniers jours.
L'endpoint `/api/v1/stats/trend` fournit exactement la donnée sur sept jours.

## Anomalie 4 — Environnement Python non reproductible

`ia-engine/` ne contient pas de `requirements.txt`. Les dépendances ne sont documentées
qu'en prose dans le README, sans versions figées hormis `signify==0.7.1`. Les
performances mesurées du modèle ne sont donc pas reproductibles.

Dépendances utilisées d'après `main.py` et le README : `fastapi`, `uvicorn[standard]`,
`python-multipart`, `joblib`, `numpy`, `lightgbm`, `requests`, `thrember`
(depuis `git+https://github.com/FutureComputing4AI/EMBER2024.git`), `signify==0.7.1`.

## Anomalie 5 — Documentation périmée

`ia-engine/NOTES.md` explique en détail comment patcher `elastic/ember` pour le rendre
compatible avec une version récente de `lief`. Or `main.py` importe `thrember` et le
README installe depuis `FutureComputing4AI/EMBER2024`. **La note documente une
bibliothèque qui n'est plus utilisée** et fera perdre du temps à quiconque la suit.

## Anomalie 6 — Exigence RF-07 non couverte

`backend/.../config/SecurityConfig.java` :

```java
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
```

L'API est entièrement ouverte. Le cahier des charges prévoit JWT + MFA + rôles. C'est le
seul chantier conséquent restant. Conséquence directe : le champ `analyst` de
`ScanService.toDto()` est codé en dur à `"Moteur IA"` et affiché dans une colonne
« Analyst » qui n'a pas de sens sans utilisateurs.

## Anomalie 7 — Couverture de tests quasi nulle

`backend/src/test/` ne contient que `contextLoads()`. Aucun test ne protège la logique
métier de `ScanService`, qui contient pourtant des règles non triviales :

- `confiance()` renvoie `100 - score` pour un fichier sain, `score` sinon
- `weeklyTrend()` doit conserver les jours sans analyse à zéro plutôt que de les omettre
- la classification à trois niveaux dépend d'un seuil calibré, pas de 0,5

## Anomalie 8 — Fuite d'information technique

`ScanController.erreurInattendue()` renvoie au client la classe et le message de
l'exception racine. Le commentaire du code le signale déjà : à rendre générique avant
toute mise en ligne. Même remarque pour `IaEngineClient`, dont le message d'erreur
inclut désormais le détail technique — volontairement, pour le développement.

## Points à ne pas modifier

Ces éléments sont des choix assumés et documentés :

- `SimpleClientHttpRequestFactory` dans `IaEngineClient` — remplace
  `JdkClientHttpRequestFactory`, qui provoquait un `Connection reset by peer` sur les
  envois multipart vers uvicorn
- `MultiValueMap` plutôt que `MultipartBodyBuilder` — ce dernier dépend de
  `reactive-streams`, absent du projet, d'où une `NoClassDefFoundError`
- `127.0.0.1` et non `localhost` pour joindre le moteur — sous Windows, `localhost` est
  résolu en IPv6 avant IPv4 et ajoute environ 2 secondes par appel
- Le port 5432 et l'absence de Docker
