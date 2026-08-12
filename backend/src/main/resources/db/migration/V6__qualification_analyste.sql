-- Qualification d'une alerte par l'analyste.
--
-- La version precedente n'offrait qu'un avis parmi trois valeurs. Or le travail
-- d'un analyste ne se resume pas a trancher : il doit justifier son jugement et
-- ajuster la criticite selon le contexte, ce que le moteur ignore.
--
-- Un avis sans justification n'aide ni l'escalade, ni l'amelioration des regles
-- de detection.

-- Justification libre. En texte long et non en VARCHAR borne : une analyse
-- d'incident ne se contraint pas a une longueur decidee a l'avance.
ALTER TABLE scan_result ADD COLUMN analyst_comment TEXT;

-- Criticite retenue par l'analyste, quand elle differe de celle deduite du
-- verdict. Le moteur ne connait ni les actifs touches, ni l'exposition reelle :
-- un meme fichier n'a pas la meme gravite sur un poste isole et sur un serveur
-- de production.
-- Valeurs : CRITICAL, HIGH, MEDIUM. Nulle tant que l'analyste n'a rien change.
ALTER TABLE scan_result ADD COLUMN analyst_severity VARCHAR(16);
