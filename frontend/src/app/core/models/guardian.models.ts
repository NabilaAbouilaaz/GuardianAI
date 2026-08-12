export type ScanStatus = 'MALICIOUS' | 'SUSPICIOUS' | 'CLEAN' | 'PROCESSING';

export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM';
export type AlertStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED';

export interface ScanRecord {
  id: string;
  /** Identifiant réel de l'analyse, nécessaire pour demander sa justification. */
  scanId: string;
  filename: string;
  hash: string;
  status: ScanStatus;
  confidence: number;
  analyst: string;
  timestamp: string;
  size: string;
  type: string;
}

export interface AlertRecord {
  id: string;
  /** Identifiant réel de l'analyse à l'origine de l'alerte. */
  scanId: string;
  title: string;
  file: string;
  severity: AlertSeverity;
  time: string;
  analyst: string;
  status: AlertStatus;
  /** Justification rédigée par l'analyste. Nulle tant qu'aucun avis n'a été porté. */
  commentaire: string | null;
  /** Vrai lorsque la criticité a été fixée par l'analyste plutôt que déduite du verdict. */
  criticiteAjustee: boolean;
}

export interface TrendPoint {
  day: string;
  malicious: number;
  suspicious: number;
  clean: number;
}

export interface FileTypeCount {
  type: string;
  count: number;
}

/**
 * Contribution d'un groupe de caractéristiques au verdict, calculée par SHAP.
 *
 * `valeur` est exprimée en log-odds : c'est l'espace dans lequel LightGBM
 * additionne ses arbres. Son signe donne le sens, sa valeur absolue le poids
 * relatif. Elle ne s'interprète pas comme un pourcentage.
 */
export interface Contribution {
  groupe: string;
  valeur: number;
  direction: 'malveillant' | 'benin';
}

export interface ServiceStatus {
  name: string;
  status: 'OPERATIONAL' | 'DEGRADED' | 'DOWN';
  latency: string;
  uptime: string;
}

export const STATUS_COLOR: Record<ScanStatus, string> = {
  MALICIOUS: '#FF4444',
  SUSPICIOUS: '#FFB800',
  CLEAN: '#00FF88',
  PROCESSING: '#00D4FF',
};

export const SEVERITY_COLOR: Record<AlertSeverity, string> = {
  CRITICAL: '#FF4444',
  HIGH: '#FFB800',
  MEDIUM: '#00D4FF',
};
