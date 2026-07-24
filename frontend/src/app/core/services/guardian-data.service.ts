import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import {
  AlertRecord,
  FileTypeCount,
  ScanRecord,
  ServiceStatus,
  TrendPoint,
} from '../models/guardian.models';

/**
 * GuardianDataService centralise l'accès aux données du dashboard.
 *
 * Aujourd'hui : renvoie des données simulées (mock) via `of(...)`.
 * Demain : il suffira de remplacer le corps de chaque méthode par un appel
 * `this.http.get<...>('/api/...')` sans toucher aux composants qui
 * consomment ce service (ils dépendent uniquement des Observables).
 */
@Injectable({ providedIn: 'root' })
export class GuardianDataService {
  getTrend(): Observable<TrendPoint[]> {
    return of([
      { day: 'Mon', malicious: 12, suspicious: 8, clean: 134 },
      { day: 'Tue', malicious: 19, suspicious: 14, clean: 156 },
      { day: 'Wed', malicious: 7, suspicious: 11, clean: 198 },
      { day: 'Thu', malicious: 23, suspicious: 17, clean: 142 },
      { day: 'Fri', malicious: 31, suspicious: 22, clean: 167 },
      { day: 'Sat', malicious: 9, suspicious: 6, clean: 89 },
      { day: 'Sun', malicious: 14, suspicious: 9, clean: 112 },
    ]);
  }

  getFileTypeBreakdown(): Observable<FileTypeCount[]> {
    return of([
      { type: '.exe', count: 342 },
      { type: '.dll', count: 218 },
      { type: '.pdf', count: 189 },
      { type: '.docx', count: 156 },
      { type: '.ps1', count: 98 },
      { type: '.zip', count: 87 },
      { type: '.sh', count: 64 },
    ]);
  }

  getRecentScans(): Observable<ScanRecord[]> {
    return of([
      { id: 'SCN-8821', filename: 'invoice_Q3_2026.exe', hash: 'a3f2c1d8e9b047a1', status: 'MALICIOUS', confidence: 97.4, analyst: 'N. Abouilaaz', timestamp: '2026-07-24 14:32:11', size: '2.4 MB', type: 'PE32' },
      { id: 'SCN-8820', filename: 'update_patch_v2.dll', hash: 'b7e4a9f2c1d830f5', status: 'SUSPICIOUS', confidence: 72.1, analyst: 'A. Zeryouel', timestamp: '2026-07-24 14:28:44', size: '1.1 MB', type: 'PE32' },
      { id: 'SCN-8819', filename: 'annual_report_2025.pdf', hash: 'c2d5f8a1b4e73d90', status: 'CLEAN', confidence: 99.8, analyst: 'N. Abouilaaz', timestamp: '2026-07-24 14:21:05', size: '4.8 MB', type: 'PDF' },
      { id: 'SCN-8818', filename: 'deploy_script.ps1', hash: 'd9b3e7f2c5a14082', status: 'MALICIOUS', confidence: 94.6, analyst: 'A. Zeryouel', timestamp: '2026-07-24 14:17:30', size: '18 KB', type: 'Script' },
      { id: 'SCN-8817', filename: 'client_data_export.zip', hash: 'e1f4d8b2c7a93051', status: 'SUSPICIOUS', confidence: 58.3, analyst: 'N. Abouilaaz', timestamp: '2026-07-24 14:09:18', size: '12.2 MB', type: 'Archive' },
      { id: 'SCN-8816', filename: 'teams_plugin.dll', hash: 'f6c2a9e4b1d73082', status: 'CLEAN', confidence: 98.7, analyst: 'A. Zeryouel', timestamp: '2026-07-24 13:58:44', size: '3.3 MB', type: 'PE32' },
    ]);
  }

  getAlerts(): Observable<AlertRecord[]> {
    return of([
      { id: 'ALT-441', title: 'Ransomware Behavior Detected', file: 'invoice_Q3_2026.exe', severity: 'CRITICAL', time: '14:32', analyst: 'N. Abouilaaz', status: 'INVESTIGATING' },
      { id: 'ALT-440', title: 'PowerShell Obfuscation Pattern', file: 'deploy_script.ps1', severity: 'CRITICAL', time: '14:17', analyst: 'A. Zeryouel', status: 'OPEN' },
      { id: 'ALT-439', title: 'Suspicious Registry Modification', file: 'update_patch_v2.dll', severity: 'HIGH', time: '14:28', analyst: 'N. Abouilaaz', status: 'OPEN' },
      { id: 'ALT-438', title: 'Encrypted C2 Communication', file: 'client_data_export.zip', severity: 'MEDIUM', time: '14:09', analyst: 'A. Zeryouel', status: 'OPEN' },
    ]);
  }

  getServiceStatuses(): Observable<ServiceStatus[]> {
    return of([
      { name: 'API Gateway', status: 'OPERATIONAL', latency: '12ms', uptime: '99.97%' },
      { name: 'AI Inference Engine', status: 'OPERATIONAL', latency: '1.4s', uptime: '99.81%' },
      { name: 'FastAPI Microservice', status: 'OPERATIONAL', latency: '48ms', uptime: '99.95%' },
      { name: 'PostgreSQL', status: 'OPERATIONAL', latency: '4ms', uptime: '99.99%' },
    ]);
  }

  /** Simule une analyse asynchrone d'un fichier envoyé côté client. */
  simulateScan(fileName: string): Observable<{ status: 'CLEAN' | 'SUSPICIOUS' | 'MALICIOUS' }> {
    const outcomes: Array<'CLEAN' | 'SUSPICIOUS' | 'MALICIOUS'> = [
      'CLEAN', 'CLEAN', 'CLEAN', 'SUSPICIOUS', 'MALICIOUS',
    ];
    const status = outcomes[Math.floor(Math.random() * outcomes.length)];
    return of({ status });
  }
}
