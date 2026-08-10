# Lancer GuardianAI en local

La plateforme se compose de quatre éléments. Trois d'entre eux tournent en parallèle et demandent chacun un terminal dédié ; la base de données, elle, est un service Windows démarré automatiquement au lancement de la machine.

L'ordre a son importance : la base d'abord, puis le moteur d'analyse, puis le backend, et enfin l'interface.

## 1. La base de données

La base tourne sur l'installation PostgreSQL native de la machine, sur le port standard 5432. Aucun conteneur n'est utilisé.

Le service démarre avec Windows, il n'y a donc normalement rien à faire. Pour le vérifier, dans PowerShell :

```powershell
Get-Service -Name postgresql*
```

Le statut doit être `Running`. Si ce n'est pas le cas :

```powershell
Start-Service -Name postgresql*
```

Si la base doit être recréée depuis zéro, se connecter avec **SQL Shell (psql)** en tant que superutilisateur `postgres`, puis :

```sql
CREATE USER guardianai_user WITH PASSWORD 'motdepasse_fort';
CREATE DATABASE guardianai OWNER guardianai_user;
GRANT ALL PRIVILEGES ON DATABASE guardianai TO guardianai_user;
```

Le schéma n'est pas à créer à la main : Flyway applique la migration `V1__create_scan_result.sql` au premier démarrage du backend.

> **Note d'architecture.** Le projet a d'abord utilisé un conteneur Docker exposé sur le port 5433, afin de cohabiter avec l'installation PostgreSQL déjà présente. Cette solution a été abandonnée : elle faisait doublon avec le serveur natif et mobilisait plusieurs dizaines de gigaoctets pour aucun apport fonctionnel. La conteneurisation reste pertinente pour un déploiement multi-machines, mais pas dans ce contexte de développement local.

## 2. Le moteur d'analyse

**Terminal 1**, à garder ouvert :

```bash
cd ia-engine
venv\Scripts\activate
uvicorn main:app --port 8000
```

Attendre la ligne `Application startup complete.` La documentation de l'API est alors consultable sur `http://127.0.0.1:8000/docs`.

Sous Linux ou macOS, remplacer `venv\Scripts\activate` par `source venv/bin/activate`.

## 3. Le backend

**Terminal 2**, à garder ouvert :

```bash
cd backend
mvn spring-boot:run
```

Attendre la ligne `Started BackendApplication`. Deux repères utiles dans les logs : la ligne `Moteur IA configure sur http://127.0.0.1:8000` confirme que le connecteur est en place, et les lignes Flyway confirment que le schéma de base est à jour.

## 4. L'interface

**Terminal 3**, à garder ouvert :

```bash
cd frontend
npm install
ng serve
```

`npm install` n'est nécessaire qu'à la première utilisation. L'interface est ensuite accessible sur `http://localhost:4200`.

## 5. Se connecter

L'API est fermée depuis la mise en place de l'authentification (RF-07). Deux comptes existent sur une installation neuve :

| Identifiant | Mot de passe initial | Rôle |
|---|---|---|
| `admin` | `Adm1n-Guardian-2026` | Administrateur |
| `analyste` | `An4lyste-Guardian-2026` | Analyste |

**Ces mots de passe cessent d'être valides dès la première connexion** : l'application impose leur renouvellement avant de donner accès aux vues métier. Les publier ici ne crée donc pas de vulnérabilité durable — c'est précisément le rôle de cette contrainte.

Le nouveau mot de passe doit faire au moins douze caractères et contenir une lettre et un chiffre.

En cas de mot de passe oublié, ou pour repartir d'un état propre, un script remet les deux comptes à leur valeur initiale. Depuis **SQL Shell (psql)** :

```sql
\c guardianai
\i 'C:/Users/aboui/OneDrive/Desktop/New folder/GuardianAI/backend/scripts/reinitialiser_comptes.sql'
```

Il lève aussi tout blocage en cours — cinq échecs consécutifs verrouillent un compte pendant quinze minutes.

## Vérifier que la chaîne fonctionne

Dans un quatrième terminal, sans rien arrêter :

```bash
curl http://127.0.0.1:8080/api/v1/status
```

Les trois composants doivent apparaître en `OPERATIONAL`. Cet endpoint reste accessible sans authentification, afin de permettre un diagnostic même quand la connexion est en cause.

L'analyse d'un fichier, elle, exige un jeton. On l'obtient par la connexion :

```bash
curl -X POST -H "Content-Type: application/json" ^
  -d "{\"username\":\"analyste\",\"password\":\"VOTRE_MOT_DE_PASSE\"}" ^
  http://localhost:8080/api/v1/auth/login
```

Puis on le présente à chaque appel :

```bash
curl -H "Authorization: Bearer LE_JETON" ^
  -F "file=@C:/Windows/System32/notepad.exe" ^
  http://localhost:8080/api/v1/scan
```

Le fichier traverse alors toute la chaîne : terminal, backend Java, moteur Python, enregistrement en base, retour du verdict en JSON. Un résultat avec `"status":"CLEAN"` signifie que tout fonctionne.

Le plus simple reste toutefois de passer par l'interface, qui gère le jeton pour vous.

Enfin, pour voir l'historique enregistré :

```bash
curl http://127.0.0.1:8080/api/v1/scans/recent
```

## Tout arrêter

Dans chaque terminal, `Ctrl+C`.

La base de données n'a pas besoin d'être arrêtée : c'est un service Windows dont la consommation au repos est négligeable. Les données sont conservées sur le disque et survivent aux redémarrages de la machine.

## En cas de problème

**Le backend ne démarre pas et parle d'authentification.** Vérifier que l'utilisateur `guardianai_user` existe bien et que son mot de passe correspond à celui attendu par la configuration. Pour le réinitialiser, dans psql en tant que `postgres` : `ALTER USER guardianai_user WITH PASSWORD 'motdepasse_fort';`

**Le backend ne démarre pas et parle de connexion refusée.** Le service PostgreSQL est arrêté. Le relancer avec `Start-Service -Name postgresql*`.

**Le statut du moteur IA affiche DOWN.** Le terminal 1 n'est pas lancé, ou uvicorn a été arrêté.

**L'analyse renvoie une erreur 503.** Le backend joint le moteur mais celui-ci ne répond pas dans le délai imparti. Vérifier le terminal 1.

**L'analyse renvoie une erreur 422.** Le fichier a bien été reçu mais le moteur ne sait pas l'analyser : format non géré ou fichier corrompu. C'est un comportement attendu sur un fichier texte, par exemple.

**Le port est déjà utilisé.** Repérer le processus concerné avec `netstat -ano | findstr :8080`, puis l'arrêter ou changer de port.