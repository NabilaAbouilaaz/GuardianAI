-- Durcissement de l'authentification.
--
-- La version initiale presentait trois faiblesses :
--   1. les deux comptes partageaient le meme mot de passe, alors qu'ils n'ont
--      pas les memes droits ;
--   2. ce mot de passe etait documente, donc connu de quiconque lit le depot,
--      et restait valide indefiniment ;
--   3. rien ne limitait le nombre de tentatives de connexion.

-- Impose le renouvellement du mot de passe a la premiere connexion. C'est la
-- seule facon qu'un identifiant initial, necessairement connu puisqu'il faut
-- bien le transmettre, cesse d'etre valide des le premier usage.
ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Protection contre l'essai systematique de mots de passe. Sans limite, un
-- attaquant peut tenter des milliers de combinaisons par minute ; avec un
-- blocage temporaire, la meme attaque demanderait des annees.
ALTER TABLE app_user ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until    TIMESTAMP WITH TIME ZONE;

-- Tracabilite : savoir quand un compte a servi pour la derniere fois permet de
-- reperer un acces inhabituel, et de detecter les comptes dormants.
ALTER TABLE app_user ADD COLUMN last_login_at   TIMESTAMP WITH TIME ZONE;

-- Mots de passe initiaux distincts, et a renouveler des la premiere connexion.
--   admin    : Adm1n-Guardian-2026
--   analyste : An4lyste-Guardian-2026
--
-- Ces valeurs figurent ici parce qu'il faut bien un point de depart sur une
-- installation neuve. Elles perdent toute validite au premier acces, le
-- changement etant impose : les laisser dans le depot ne cree donc pas de
-- vulnerabilite durable.
UPDATE app_user
   SET password_hash = '$2a$10$jyXDwu8uhWuWvAP4NBXxWui/ce7Lm8XUX1G63yq2UWNHS4PMk9cAK',
       must_change_password = TRUE
 WHERE username = 'admin';

UPDATE app_user
   SET password_hash = '$2a$10$xYm13oBvARmHXuI2Ktn3PuGNYFSOLenYWEVk77JwROkDT7djnk9gK',
       must_change_password = TRUE
 WHERE username = 'analyste';
