-- Historique des analyses de fichiers.
-- Chaque ligne est une preuve d'analyse : fichier, empreinte, verdict, modele utilise.
-- Aucune suppression prevue : la table est en ajout seul (exigence de tracabilite RF-11).

CREATE TABLE scan_result (
    id             UUID PRIMARY KEY,
    filename       VARCHAR(512)     NOT NULL,
    sha256         VARCHAR(64)      NOT NULL,
    size_bytes     BIGINT           NOT NULL,
    file_type      VARCHAR(32),
    classification VARCHAR(16)      NOT NULL,
    score          DOUBLE PRECISION NOT NULL,
    threshold      DOUBLE PRECISION,
    model_version  VARCHAR(64),
    duration_ms    DOUBLE PRECISION,
    analyzed_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Recherche par empreinte (RF-12) et affichage de l'historique recent.
CREATE INDEX idx_scan_result_sha256 ON scan_result (sha256);
CREATE INDEX idx_scan_result_analyzed_at ON scan_result (analyzed_at DESC);
