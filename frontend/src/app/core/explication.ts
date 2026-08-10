import { Contribution, ScanStatus } from './models/guardian.models';

/**
 * Mise en forme des explications SHAP pour un lecteur non specialiste.
 *
 * Les valeurs renvoyees par le moteur sont en log-odds. Elles sont exactes et
 * verifiables, mais personne n'en a l'intuition : "-3.13" ne dit rien a un
 * utilisateur, ni meme a la plupart des techniciens. Ce module les traduit en
 * langage courant sans les remplacer — l'interface conserve les valeurs brutes
 * derriere un repli, car ce sont elles qui font foi.
 */

/**
 * Formulation accessible de chaque groupe de caracteristiques.
 *
 * On decrit ce que le groupe observe, pas son nom technique : "les textes qu'il
 * contient" plutot que "chaines de caracteres".
 */
const FORMULATIONS: Record<string, string> = {
  'Informations generales': 'ses caractéristiques générales',
  "Histogramme d'octets": 'la composition de son contenu',
  'Entropie des octets': "l'entropie de son contenu",
  'Chaines de caracteres': "les textes qu'il contient",
  'En-tetes PE': 'ses en-têtes de programme',
  'Sections': "l'organisation de ses sections",
  'Fonctions importees': "les fonctions système qu'il utilise",
  'Fonctions exportees': "les fonctions qu'il expose",
  'Repertoires de donnees': 'ses ressources internes',
  'En-tete Rich': 'les traces laissées par son compilateur',
  'Signature numerique': 'sa signature numérique',
  'Anomalies de structure PE': 'des anomalies dans sa structure',
};

const VERDICTS: Record<ScanStatus, string> = {
  CLEAN: 'bénin',
  SUSPICIOUS: 'suspect',
  MALICIOUS: 'malveillant',
  PROCESSING: 'en cours d\'analyse',
};

function formuler(groupe: string): string {
  return FORMULATIONS[groupe] ?? groupe.toLowerCase();
}

/** Poids le plus fort de la liste, utilise comme reference de normalisation. */
function poidsMax(contributions: Contribution[]): number {
  return Math.max(...contributions.map((c) => Math.abs(c.valeur)), 0.0001);
}

/**
 * Importance relative d'une contribution, sur une echelle a trois niveaux.
 *
 * Une echelle qualitative est plus honnete qu'un pourcentage : les log-odds ne
 * se convertissent pas en part de responsabilite, et afficher "42 %" laisserait
 * croire a une precision qui n'existe pas.
 */
export function niveau(c: Contribution, contributions: Contribution[]): string {
  const part = Math.abs(c.valeur) / poidsMax(contributions);
  if (part >= 0.5) return 'Déterminant';
  if (part >= 0.2) return 'Important';
  return 'Marginal';
}

/** Largeur de barre, normalisee sur la contribution la plus forte. */
export function largeur(c: Contribution, contributions: Contribution[]): number {
  return (Math.abs(c.valeur) / poidsMax(contributions)) * 100;
}

/**
 * Resume du verdict en une phrase.
 *
 * Construit par gabarit et non par un modele de langage : dans un outil de
 * securite, une formulation generee librement pourrait affirmer avec aplomb
 * quelque chose que l'analyse ne dit pas. Ici, la phrase ne peut mentionner que
 * des groupes reellement calcules.
 */
export function resume(contributions: Contribution[], statut: ScanStatus): string {
  if (!contributions.length) {
    return "Aucune justification n'a été enregistrée pour cette analyse.";
  }

  const verdict = VERDICTS[statut] ?? 'analysé';
  const versMalveillant = statut === 'MALICIOUS' || statut === 'SUSPICIOUS';

  // On ne retient que ce qui a pousse dans le sens du verdict rendu : citer un
  // facteur contraire comme motif serait trompeur.
  const sens = versMalveillant ? 'malveillant' : 'benin';
  const moteurs = contributions.filter((c) => c.direction === sens).slice(0, 2);

  if (!moteurs.length) {
    return `Ce fichier a été jugé ${verdict}, sans qu'un groupe de caractéristiques ne domine nettement.`;
  }

  const motifs = moteurs.map((c) => formuler(c.groupe)).join(' et ');
  let phrase = `Ce fichier a été jugé ${verdict} principalement d'après ${motifs}.`;

  // Un facteur allant a contre-courant merite d'etre signale : c'est souvent la
  // que se trouve la nuance utile a un analyste.
  const contraires = contributions.filter((c) => c.direction !== sens);
  if (contraires.length) {
    const plusFort = contraires[0];
    if (Math.abs(plusFort.valeur) / poidsMax(contributions) >= 0.2) {
      phrase += ` À l'inverse, ${formuler(plusFort.groupe)} tire dans l'autre sens.`;
    }
  }

  return phrase;
}
