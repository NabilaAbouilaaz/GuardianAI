export type ScanStatus = 'MALICIOUS' | 'SUSPICIOUS' | 'CLEAN' | 'PROCESSING';

export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM';
export type AlertStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED';

export interface ScanRecord {
  id: string;
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
  title: string;
  file: string;
  severity: AlertSeverity;
  time: string;
  analyst: string;
  status: AlertStatus;
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

export interface ShapFeature {
  feature: string;
  score: number;
  direction: 'malicious' | 'benign';
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
