-- Avis de l'analyste sur un verdict.
--
-- L'interface proposait deux boutons, « FALSE POSITIVE » et « RESOLVE », relies
-- a aucun traitement : cliquer dessus ne produisait rien. Un bouton mort est
-- pire qu'un bouton absent, l'utilisateur croyant avoir agi.
--
-- Au-dela de la coherence de l'interface, cette table de retour a une valeur
-- propre : elle constitue le seul moyen de mesurer le taux de faux positifs
-- reel en exploitation, par opposition aux 1,90 % mesures sur le jeu de test.

-- CONFIRME  : l'analyste valide le verdict du moteur.
-- FAUX_POSITIF : le fichier est sain malgre un verdict inquietant.
-- TRAITE    : l'incident est clos, sans se prononcer sur le verdict.
ALTER TABLE scan_result ADD COLUMN analyst_feedback VARCHAR(16);

-- Qui s'est prononce, et quand. Sans ces deux colonnes, l'avis serait une
-- opinion anonyme et non datee, donc sans valeur probante (RF-11).
ALTER TABLE scan_result ADD COLUMN feedback_by UUID REFERENCES app_user (id);
ALTER TABLE scan_result ADD COLUMN feedback_at TIMESTAMP WITH TIME ZONE;

-- Les avis sont rares par rapport au volume d'analyses : un index partiel suffit
-- et reste compact.
CREATE INDEX idx_scan_result_feedback
    ON scan_result (analyst_feedback)
 WHERE analyst_feedback IS NOT NULL;
