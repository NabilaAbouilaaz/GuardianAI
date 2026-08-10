-- Reinitialisation des comptes de la plateforme.
--
-- A executer dans psql, connecte a la base guardianai :
--     \c guardianai
--     \i 'C:/Users/aboui/OneDrive/Desktop/New folder/GuardianAI/backend/scripts/reinitialiser_comptes.sql'
--
-- Ce fichier est volontairement HORS du dossier db/migration : Flyway execute
-- automatiquement tout ce qui s'y trouve, et une reinitialisation des mots de
-- passe a chaque demarrage serait exactement l'inverse de ce qu'on veut.
--
-- Apres execution :
--     admin    -> Adm1n-Guardian-2026
--     analyste -> An4lyste-Guardian-2026
-- Les deux comptes redemandent un renouvellement des la connexion suivante, et
-- tout blocage en cours est leve.

UPDATE app_user
   SET password_hash        = '$2a$10$jyXDwu8uhWuWvAP4NBXxWui/ce7Lm8XUX1G63yq2UWNHS4PMk9cAK',
       must_change_password = TRUE,
       failed_attempts      = 0,
       locked_until         = NULL,
       enabled              = TRUE
 WHERE username = 'admin';

UPDATE app_user
   SET password_hash        = '$2a$10$xYm13oBvARmHXuI2Ktn3PuGNYFSOLenYWEVk77JwROkDT7djnk9gK',
       must_change_password = TRUE,
       failed_attempts      = 0,
       locked_until         = NULL,
       enabled              = TRUE
 WHERE username = 'analyste';

-- Controle : les deux lignes doivent afficher must_change_password = t
SELECT username, display_name, role, must_change_password, failed_attempts, locked_until
  FROM app_user
 ORDER BY username;
