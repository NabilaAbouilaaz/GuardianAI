# GuardianAI

Plateforme de détection de malwares par intelligence artificielle, développée pour TEC Group.

GuardianAI analyse un fichier et rend un verdict — bénin, suspect ou malveillant — accompagné d'un score de confiance, en moins d'une seconde. Contrairement aux antivirus classiques qui comparent les fichiers à une liste de signatures connues, le moteur apprend à reconnaître ce qui caractérise un fichier malveillant. Il peut donc détecter des menaces qu'il n'a jamais vues, y compris celles dont le code a été modifié pour échapper aux détections habituelles.

## Équipe

**Achraf Zeryouel** — Ingénieur Full-Stack et Architecte. Il a conçu l'architecture globale et développé le backend Spring Boot, le frontend Angular, la base de données, la sécurité applicative et la conteneurisation.

**Nabila Abouilaaz** — Spécialiste Intelligence Artificielle et Cybersécurité. Elle a conçu le microservice d'analyse : constitution des jeux de données, entraînement et évaluation des modèles de détection, calibration des seuils, sécurisation du modèle.

## Architecture

Le projet est découpé en trois composants indépendants qui communiquent en HTTP.

Le dossier `backend/` contient l'API REST Spring Boot : elle porte la logique métier, l'authentification et l'enregistrement des analyses. Le dossier `frontend/` contient l'interface Angular, utilisée pour déposer des fichiers et superviser l'activité de détection. Le dossier `ia-engine/` contient le moteur d'analyse, un microservice Python qui reçoit un fichier et retourne un verdict.

Le backend joue le rôle de chef d'orchestre : il reçoit les fichiers envoyés depuis l'interface, les transmet au moteur d'analyse, puis conserve et diffuse les résultats. Cette séparation permet de faire évoluer le modèle de détection sans toucher au reste de la plateforme.

## Résultats du moteur de détection

Les mesures ci-dessous ont été obtenues sur le jeu de test EMBER2024. L'évaluation utilise un découpage chronologique : le modèle est entraîné sur les fichiers les plus anciens et testé sur les plus récents. Cela reproduit la situation réelle, où le moteur doit affronter des menaces apparues après son entraînement — un découpage aléatoire donnerait des chiffres plus flatteurs mais trompeurs.

Le moteur détecte **97,27 %** des fichiers malveillants, pour un objectif fixé à 95 % minimum.

Il génère **2,00 %** de fausses alertes, ce qui correspond exactement au plafond de 2 % fixé par le cahier des charges. Ce chiffre est important : un outil qui crie au loup trop souvent finit par être ignoré des analystes.

Le temps d'analyse est de **0,42 seconde en médiane**, et de **2,22 secondes au 95e percentile**, donc sous la limite de 3 secondes exigée.

Enfin, le moteur détecte **75,72 %** des malwares dits évasifs : des fichiers qui avaient échappé à environ 70 antivirus commerciaux au moment de leur apparition. C'est la démonstration la plus concrète de l'intérêt de l'approche : l'analyse par apprentissage rattrape une partie de ce que les signatures laissent passer.

En complément, le moteur a été testé sur 50 exécutables système Windows parfaitement légitimes : **aucun n'a été signalé à tort**.

## Démarrage rapide

### Le moteur d'analyse

```bash
cd ia-engine
python -m venv venv
venv\Scripts\activate
pip install fastapi uvicorn[standard] python-multipart joblib numpy lightgbm requests
pip install git+https://github.com/FutureComputing4AI/EMBER2024.git
pip install "signify==0.7.1"
uvicorn main:app --port 8000
```

Sous Linux ou macOS, remplacer `venv\Scripts\activate` par `source venv/bin/activate`.

La documentation interactive de l'API est ensuite accessible sur `http://127.0.0.1:8000/docs`. Elle permet de tester le moteur directement depuis le navigateur, sans écrire une ligne de code.

Trois points d'entrée sont disponibles. `GET /health` indique si le service répond. `GET /model-info` renvoie le modèle actuellement en service, le seuil de décision appliqué et les performances mesurées. `POST /predict` analyse un fichier, dans la limite de 200 Mo.

Voici la réponse renvoyée par une analyse :

```json
{
  "filename": "notepad.exe",
  "sha256": "84b484fd3636f2ca3e468d2821d97aacde8a143a2724a3ae65f48a33ca2fd258",
  "taille_octets": 360448,
  "classification": "benin",
  "score_malveillance": 0.04,
  "seuil_applique": 66.38,
  "model_version": "lightgbm_ember2024_v2",
  "cache": false,
  "duree_ms": 416.2
}
```

### Le backend

Une base PostgreSQL est nécessaire avant de lancer le backend. Elle s'appuie sur une installation PostgreSQL locale écoutant sur le port 5432. Créer une fois pour toutes la base et son utilisateur, depuis psql en tant que superutilisateur `postgres` :

```sql
CREATE USER guardianai_user WITH PASSWORD 'motdepasse_fort';
CREATE DATABASE guardianai OWNER guardianai_user;
GRANT ALL PRIVILEGES ON DATABASE guardianai TO guardianai_user;
```

Le schéma est ensuite créé et versionné par Flyway au démarrage du backend :

```bash
cd backend
mvn spring-boot:run
```

Les paramètres de connexion sont surchargeables sans modifier le code, via les variables d'environnement `DB_URL`, `DB_USER` et `DB_PASSWORD` — ce qui permet de pointer vers une base distante en recette ou en production.

### Le frontend

```bash
cd frontend
npm install
ng serve
```

L'interface est alors accessible sur `http://localhost:4200`.

## Comment fonctionne le moteur d'analyse

Chaque fichier est d'abord traduit en 2 568 valeurs numériques qui décrivent sa structure : répartition des octets, mesure du désordre interne, contenu des en-têtes, découpage en sections, bibliothèques et fonctions utilisées, présence d'une signature numérique, chaînes de caractères lisibles. Cette lecture se fait sans jamais exécuter le fichier, ce qui écarte tout risque pendant l'analyse.

Ces valeurs sont ensuite soumises à un modèle LightGBM, une méthode d'apprentissage fondée sur des arbres de décision successifs, particulièrement efficace sur ce type de données. Le modèle a été entraîné sur EMBER2024, un jeu de données de référence rassemblant les caractéristiques de 3,2 millions de fichiers analysés entre septembre 2023 et décembre 2024.

Le modèle produit une probabilité de malveillance. Le seuil qui transforme cette probabilité en verdict n'est pas laissé à la valeur par défaut de 50 %, qui n'a aucune justification : il a été calculé pour garantir moins de 2 % de fausses alertes, comme l'exige le cahier des charges. Un fichier déjà analysé est reconnu par son empreinte SHA-256 et reçoit une réponse immédiate, sans nouveau calcul.

## Tests

```bash
cd ia-engine
python test_predict.py --n 50
```

Ce script analyse un lot d'exécutables système Windows, tous légitimes, et mesure le taux de fausses alertes ainsi que les temps de réponse. Il distingue le temps passé dans le moteur du temps passé sur le réseau, ce qui évite d'attribuer au modèle une lenteur qui vient d'ailleurs.

## Limites connues

Le modèle est entraîné sur des exécutables Windows .NET et 64 bits. L'extension aux binaires 32 bits, aux documents bureautiques et aux archives reste à réaliser.

L'analyse comportementale en environnement isolé, prévue au cahier des charges, n'est pas encore implémentée : le moteur repose aujourd'hui sur l'analyse statique seule, c'est-à-dire l'examen de la structure du fichier sans l'exécuter.

L'explicabilité des verdicts, qui permettra à un analyste de savoir quelles caractéristiques ont motivé une décision, est en cours de développement.

## Documentation

Le cahier des charges du projet, établi avec TEC Group en juillet 2026, définit le périmètre et les objectifs chiffrés. Le fichier `ia-engine/NOTES.md` regroupe les notes techniques utiles à la reconstitution de l'environnement du moteur d'analyse.