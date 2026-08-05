# GuardianAI — État du projet et travail restant

Revue effectuée le 31 juillet 2026, sur la branche `main`.

## Où en est le projet

Le socle technique est solide et largement plus avancé qu'un prototype. Le moteur de
détection fonctionne et ses performances sont mesurées sur un protocole défensable.
L'API REST est complète et correctement structurée. La persistance est en place avec
migrations versionnées. L'interface est dessinée et navigable.

Le décalage se situe ailleurs : **plusieurs écrans affichent des données fabriquées
plutôt que les résultats réels du système**. Un utilisateur qui découvre l'application
croit voir une plateforme opérationnelle alors qu'il regarde une maquette. C'est le
point à traiter en priorité, avant toute nouvelle fonctionnalité.

---

## P0 — À faire avant toute démonstration

Ces points concernent la crédibilité de ce qui est montré. Tant qu'ils ne sont pas
réglés, une démonstration expose à des questions auxquelles on ne peut pas répondre.

### 1. Valider le branchement du frontend

`guardian-data.service.ts` interrogeait des tableaux codés en dur ; il appelle
désormais l'API réelle. **Reste à tester** : déposer un fichier depuis l'interface,
vérifier le verdict affiché, puis confirmer en base que la ligne a bien été écrite.

```sql
SELECT filename, classification, score, analyzed_at FROM scan_result ORDER BY analyzed_at DESC;
```

### 2. Traiter le panneau SHAP de la page Alerts

`alerts.component.ts` contient encore des facteurs d'explication inventés :

```typescript
{ feature: 'Mass file encryption loop', score: 0.94, direction: 'malicious' },
{ feature: 'C2 beacon pattern (TLS 1.2)', score: 0.71, direction: 'malicious' },
```

Ces indicateurs décrivent des **comportements dynamiques** — chiffrement en masse,
communication vers un serveur de commande. Le moteur réalise une analyse purement
statique et ne peut structurellement pas les observer. Un examinateur qui connaît le
domaine le verra immédiatement.

Deux issues possibles :

- **Masquer le panneau** en attendant. Coût : quelques minutes. Honnête.
- **Implémenter réellement SHAP.** La bibliothèque `shap` s'interface nativement avec
  LightGBM via `TreeExplainer`. Le moteur exposerait les caractéristiques ayant le plus
  pesé sur le verdict, et l'interface les afficherait telles quelles.

La seconde option est nettement préférable sur le fond : l'explicabilité est le
reproche classique fait aux modèles de détection, et savoir y répondre est un
argument fort. Le travail se situe côté moteur, donc dans le périmètre IA.

### 3. Créer un `requirements.txt` pour le moteur

Les dépendances Python ne sont documentées qu'en prose dans le README, sous forme
d'une suite de commandes `pip install`. Aucune version n'est figée hormis `signify`.
Conséquence : personne ne peut reproduire l'environnement de manière fiable, et les
résultats mesurés ne sont pas reproductibles — ce qui affaiblit les chiffres annoncés.

### 4. Corriger `ia-engine/NOTES.md`

Le fichier décrit en détail comment patcher `elastic/ember` pour le rendre compatible
avec une version récente de `lief`. Or `main.py` importe `thrember`, et le README
installe depuis `FutureComputing4AI/EMBER2024`. **La note documente une bibliothèque
qui n'est plus celle utilisée.** Quiconque suit ces instructions perdra du temps sur un
problème qui n'existe plus.

---

## P1 — Exigences du cahier des charges non couvertes

### 5. RF-07 — Authentification

`SecurityConfig.java` est explicite :

```java
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
```

L'API est entièrement ouverte. Le commentaire annonce « JWT + MFA + rôles ». C'est la
seule exigence fonctionnelle identifiée comme totalement absente.

Périmètre réaliste à court terme : authentification par JWT et deux rôles
(analyste / administrateur). Le MFA peut être présenté comme une perspective
documentée plutôt que livré à moitié.

### 6. Tests automatisés

Le projet contient un unique test :

```java
@Test
void contextLoads() { }
```

Il vérifie que l'application démarre, rien de plus. Aucun test ne couvre la logique
métier, alors qu'elle contient des règles non triviales qui mériteraient d'être
protégées : le calcul de confiance (`100 - score` pour un fichier sain), la
classification à trois niveaux autour du seuil calibré, la construction de la tendance
hebdomadaire qui doit conserver les jours vides.

Quelques tests ciblés sur `ScanService` valent mieux qu'une couverture large et
superficielle.

### 7. Le champ « analyste » est fictif

`ScanService.toDto()` renseigne systématiquement `"Moteur IA"`, et l'interface affiche
cette valeur dans une colonne « Analyst ». Tant qu'il n'y a pas d'utilisateurs
authentifiés, cette colonne n'a pas de sens. À traiter avec le point 5, ou à retirer
de l'affichage en attendant.

---

## P2 — Finition et robustesse

### 8. Incohérence des compteurs du tableau de bord

Les cartes affichent « MALICIOUS (7D) », « SUSPICIOUS (7D) », « CLEAN (7D) », mais sont
calculées ainsi :

```typescript
get totalMalicious(): number {
  return this.recentScans.filter((s) => s.status === 'MALICIOUS').length;
}
```

`recentScans` renvoie les **20 dernières analyses**, pas les sept derniers jours. Le
libellé et le calcul divergent. L'endpoint `/stats/trend` fournit précisément la
donnée sur sept jours : c'est lui qu'il faut agréger.

Le défaut passait inaperçu avec des données simulées cohérentes ; il deviendra visible
dès que le volume réel augmentera.

### 9. Cycle de vie des alertes

`ScanService.alerts()` le signale : « Version provisoire ». Toute alerte est créée avec
le statut `OPEN`, sans possibilité d'assignation ni de clôture. Les boutons
« FALSE POSITIVE » et « RESOLVE » de l'interface ne sont reliés à aucun traitement.

### 10. Gestionnaire d'exceptions à durcir

`ScanController` renvoie au client la classe et le message de l'exception racine. Le
code le signale lui-même : « À retirer ou à rendre générique avant une mise en
production. » Cette information est précieuse en développement et constitue une fuite
d'information technique en exploitation.

### 11. Langue de l'interface

L'application mélange l'anglais (« File Analysis », « Recent Scans », « Alert Queue »)
et le français dans les messages venant du backend. À trancher dans un sens ou dans
l'autre.

---

## Ordre de traitement suggéré

| Rang | Tâche | Effort | Effet |
|---|---|---|---|
| 1 | Tester le branchement frontend | Faible | Débloque tout le reste |
| 2 | SHAP réel ou masquage du panneau | Variable | Supprime le dernier écran fictif |
| 3 | `requirements.txt` | Faible | Rend les résultats reproductibles |
| 4 | Corriger `NOTES.md` | Faible | Évite de faire perdre du temps |
| 5 | Compteurs du tableau de bord | Faible | Corrige un écart visible |
| 6 | Tests sur `ScanService` | Moyen | Protège la logique métier |
| 7 | RF-07 authentification | Élevé | Couvre la dernière exigence absente |

Les quatre premiers points représentent une demi-journée et suppriment l'essentiel de
ce qui pourrait être reproché au projet. L'authentification est le seul chantier
véritablement conséquent.

---

## Ce qui est déjà solide

À ne pas perdre de vue lors de la présentation :

- **Le protocole d'évaluation.** Le découpage chronologique plutôt qu'aléatoire est un
  choix méthodologique juste, qui donne des chiffres moins flatteurs mais honnêtes.
  C'est exactement ce qu'on attend d'une évaluation sérieuse.
- **La calibration du seuil.** Fixer le seuil sur une contrainte de faux positifs
  plutôt que de retenir 0,5 par défaut montre une compréhension du besoin métier.
- **Le test sur binaires système réels.** `test_predict.py` mesure le taux de faux
  positifs sur des exécutables Windows légitimes. C'est une validation empirique que
  beaucoup de projets omettent.
- **La qualité du commentaire de code.** Les décisions techniques sont expliquées avec
  leur justification — cache pour tenir RNF-01, IPv4 explicite contre la latence de
  résolution IPv6, extracteur instancié une seule fois. C'est rare et cela se remarque.
