-- Explicabilite des verdicts (SHAP).
--
-- Les contributions sont enregistrees au moment de l'analyse et non recalculees
-- a la demande : la table scan_result ne conserve que l'empreinte du fichier, pas
-- le fichier lui-meme. Sans cet enregistrement, une decision deviendrait
-- inexplicable des que le fichier analyse disparait, ce qui contredirait
-- l'exigence de tracabilite RF-11.

-- Marge brute du modele, conservee pour pouvoir revalider une explication.
-- La somme des contributions ajoutee a la valeur de base donne la marge, dont la
-- sigmoide redonne le score. Une explication qui ne se recompose pas est fausse ;
-- ces deux colonnes permettent de le verifier a posteriori.
ALTER TABLE scan_result ADD COLUMN shap_base_value DOUBLE PRECISION;
ALTER TABLE scan_result ADD COLUMN shap_sum        DOUBLE PRECISION;

-- Une ligne par groupe de caracteristiques EMBER (douze aujourd'hui).
-- Table normalisee plutot qu'une colonne JSON : elle permet d'interroger les
-- contributions au travers de l'ensemble des analyses, par exemple pour savoir
-- quel groupe motive le plus souvent un verdict malveillant.
CREATE TABLE scan_contribution (
    id             UUID PRIMARY KEY,
    scan_result_id UUID             NOT NULL REFERENCES scan_result (id),
    groupe         VARCHAR(64)      NOT NULL,
    valeur         DOUBLE PRECISION NOT NULL,
    -- 'malveillant' si la contribution pousse le score vers le haut, 'benin' sinon.
    direction      VARCHAR(16)      NOT NULL,
    -- Position dans le classement par poids decroissant, pour restituer l'ordre
    -- d'affichage sans avoir a le recalculer.
    rang           INTEGER          NOT NULL
);

-- Les contributions sont toujours lues pour une analyse donnee.
CREATE INDEX idx_scan_contribution_scan ON scan_contribution (scan_result_id, rang);
