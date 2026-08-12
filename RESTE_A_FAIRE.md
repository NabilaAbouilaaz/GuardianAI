# GuardianAI — État du projet et travail restant

Dernière revue : 11 août 2026, branche `main`.

## Où en est le projet

Le cahier des charges n'a plus d'exigence sans réponse. RF-07, l'authentification,
était la dernière ; elle est livrée avec jetons JWT, deux rôles, renouvellement imposé
du mot de passe initial et verrouillage temporaire après échecs répétés.

L'interface n'affiche plus aucune donnée simulée. Les résultats du modèle sont
reproductibles et leur méthode de calcul est documentée. Trente-sept tests protègent
les règles métier les moins évidentes.

---

## Ce qui a été traité

### Sortie de Docker

Le conteneur PostgreSQL faisait doublon avec l'installation native et mobilisait
plusieurs dizaines de gigaoctets. La base tourne désormais sur le service Windows,
port 5432. La conteneurisation reste pertinente pour un déploiement multi-machines,
et le choix est documenté dans `LANCER.md`.

### Branchement de l'interface sur l'API

`guardian-data.service.ts` interrogeait six tableaux codés en dur. Les écrans
affichaient des analyses fictives, un décompte de types de fichiers sans rapport avec
le compteur d'analyses, et un scan dont le verdict était **tiré au sort**. Tout passe
désormais par l'API.

### Trois défauts de fond corrigés

**`NoClassDefFoundError` masquée.** `MultipartBodyBuilder` dépend de `reactive-streams`,
absent du projet. L'erreur échappait au `catch (Exception)` — une `Error` n'est pas une
`Exception` — et remontait en 500 opaque. Remplacé par `MultiValueMap`.

**Incompatibilité du client HTTP.** `JdkClientHttpRequestFactory` provoquait un
`Connection reset by peer` sur les envois multipart vers uvicorn, alors que le moteur
répondait parfaitement en direct. Remplacé par `SimpleClientHttpRequestFactory`.

**Message d'erreur trompeur.** Le connecteur annonçait « moteur indisponible » pour
trois pannes distinctes. Il rapporte maintenant la cause réelle.

### Explicabilité SHAP

Le moteur décompose chaque verdict en contributions par groupe de caractéristiques
EMBER. Les valeurs sont enregistrées au moment de l'analyse — la base ne conserve que
l'empreinte du fichier, les recalculer plus tard serait impossible (RF-11).

L'interface présente une phrase en langage courant, une échelle qualitative, et les
valeurs brutes derrière un repli.

### Fiabilité des mesures

Le seuil était calibré sur le jeu de test puis les performances annoncées sur ce même
jeu. Il est désormais calibré sur une moitié et mesuré sur l'autre :

| | Ancienne méthode | Méthode retenue |
|---|---|---|
| Détection | 97,27 % | **97,16 %** |
| Faux positifs | 2,00 % | **1,90 %** |
| Évasifs | 75,72 % | **75,34 %** |

L'écart est faible, mais la seconde mesure permet d'affirmer que la contrainte des 2 %
tient sur des données jamais vues.

### Question `authenticode` tranchée

Le groupe `authenticode` contribuait systématiquement vers « malveillant » sur des
binaires Microsoft signés. Après vérification, l'extraction fonctionne : ces fichiers
sont signés **par catalogue**, sans signature intégrée. Le modèle réagit à un fait réel.
Limite intéressante à mentionner : il ne distingue pas « non signé » de « signé par
catalogue ».

---

## Ce qui reste

### Limite de taille — mesurée et documentée

Le cahier des charges annonce 200 Mo par fichier (UC-01). La mesure, réalisée avec
`ia-engine/inspect_memoire.py` sur douze binaires Windows, montre que l'extraction
consomme **de 9 à 42 fois la taille du fichier**.

Au pire ratio observé, 200 Mo demanderaient 8,4 Go de mémoire libre d'un seul tenant.
Sur la machine de développement, avec 2 Go disponibles, la limite tenable est d'environ
48 Mo. Aucun échec constaté jusqu'à 53,8 Mo.

La limite annoncée reste donc **conditionnée à l'environnement d'exécution**. Un fichier
trop volumineux renvoie désormais un `507` accompagné d'une estimation du besoin réel,
au lieu d'une erreur générique.

Reste ouvert : décider si la limite affichée doit être abaissée, ou si le déploiement
cible garantit la mémoire nécessaire.

### Cycle de vie des alertes — partiellement traité

Les boutons de la page Alerts enregistrent désormais l'appréciation de l'analyste :
**Confirmer**, **Faux positif** ou **Traité**. L'avis, son auteur et sa date sont
conservés, et le statut de l'alerte en découle.

Cette table de retour a une valeur propre : elle constitue le seul moyen de mesurer le
taux de faux positifs **constaté en exploitation**, par opposition aux 1,90 % mesurés
sur le jeu de test.

Reste non couvert : l'assignation d'une alerte à un analyste donné, et la notification.

### Couverture de tests

Trente-sept tests couvrent le calcul de confiance, la classification, la politique de
mot de passe et les jetons. Ne sont pas couverts : `ScanService.weeklyTrend()`, qui doit
conserver les jours sans analyse à zéro, et les parcours HTTP de bout en bout.

### Perspectives documentées

**Authentification à deux facteurs**, prévue au cahier des charges, écartée faute de
temps. Un MFA livré à moitié serait plus discutable qu'une absence assumée.

**Assistant conversationnel.** Envisagé pour vulgariser les rapports, écarté : un modèle
de langage ne peut pas décrire des comportements que l'analyse statique n'observe pas,
et inventerait. La vulgarisation est produite par gabarit, sans risque d'affabulation.

**Analyse dynamique en bac à sable.** Hors périmètre : isolation par machine virtuelle,
instrumentation, résistance à l'évasion. Le moteur détecte déjà 75 % des malwares
évasifs par analyse statique.
