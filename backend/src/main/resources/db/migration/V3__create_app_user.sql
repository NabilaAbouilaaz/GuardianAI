-- Comptes utilisateurs (exigence RF-07).
--
-- Jusqu'ici l'API etait entierement ouverte et le champ "analyste" des analyses
-- valait "Moteur IA" en dur, faute d'utilisateurs identifies.

CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    -- Identifiant de connexion. Unique et insensible a la casse cote applicatif :
    -- deux comptes ne peuvent differer que par la casse serait une source d'erreur.
    username      VARCHAR(64)  NOT NULL UNIQUE,
    -- Nom affiche dans l'interface, distinct de l'identifiant technique.
    display_name  VARCHAR(128) NOT NULL,
    -- Empreinte BCrypt, jamais le mot de passe. 60 caracteres pour l'algorithme
    -- actuel, la colonne est dimensionnee plus large pour absorber un changement.
    password_hash VARCHAR(100) NOT NULL,
    -- ANALYSTE ou ADMINISTRATEUR. Un seul role par compte : la hierarchie des
    -- droits est lineaire ici, une table d'association serait disproportionnee.
    role          VARCHAR(32)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Rattachement d'une analyse a son auteur.
-- Nullable : les analyses realisees avant l'authentification n'ont pas d'auteur,
-- et les effacer ou leur en inventer un fausserait la tracabilite (RF-11).
ALTER TABLE scan_result ADD COLUMN analyst_id UUID REFERENCES app_user (id);

-- Comptes de depart.
--
-- Les empreintes ci-dessous correspondent au mot de passe 'guardian2026' pour les
-- deux comptes. C'est un mot de passe de developpement, destine a etre change des
-- la premiere connexion reelle. Il n'ouvre l'acces qu'a une base locale.
INSERT INTO app_user (id, username, display_name, password_hash, role) VALUES
    ('11111111-1111-1111-1111-111111111111',
     'admin',
     'Administrateur',
     '$2a$10$8ZI9pO/BJmtCap9IMwShb.dCww1UNvxTjRyTwXxWNssLRbzX73yGO',
     'ADMINISTRATEUR'),
    ('22222222-2222-2222-2222-222222222222',
     'analyste',
     'Analyste securite',
     '$2a$10$GHknV8MX4k1Sd6NvnknxRe.KhdB4peZU6GrCN319BV7WrFpkMV1iS',
     'ANALYSTE');
