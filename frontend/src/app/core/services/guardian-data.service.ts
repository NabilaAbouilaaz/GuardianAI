import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AlertRecord,
  Contribution,
  FileTypeCount,
  ScanRecord,
  ServiceStatus,
  TrendPoint,
} from '../models/guardian.models';

/**
 * GuardianDataService centralise l'accès aux données du dashboard.
 *
 * Toutes les méthodes interrogent l'API REST du backend Spring Boot. Les
 * interfaces TypeScript de `guardian.models.ts` reproduisent champ pour champ
 * les records Java du paquet `dto`, aucune transformation n'est donc nécessaire
 * entre la réponse HTTP et le modèle consommé par les composants.
 */
@Injectable({ providedIn: 'root' })
export class GuardianDataService {
  /**
   * Le backend écoute sur un port distinct de celui du serveur de développement
   * Angular, d'où l'URL absolue. Les échanges sont autorisés par la configuration
   * CORS côté Spring (guardianai.cors.allowed-origins).
   */
  private readonly api = 'http://localhost:8080/api/v1';

  constructor(private readonly http: HttpClient) {}

  getTrend(): Observable<TrendPoint[]> {
    return this.http.get<TrendPoint[]>(`${this.api}/stats/trend`);
  }

  getFileTypeBreakdown(): Observable<FileTypeCount[]> {
    return this.http.get<FileTypeCount[]>(`${this.api}/stats/file-types`);
  }

  getRecentScans(): Observable<ScanRecord[]> {
    return this.http.get<ScanRecord[]>(`${this.api}/scans/recent`);
  }

  getAlerts(): Observable<AlertRecord[]> {
    return this.http.get<AlertRecord[]>(`${this.api}/alerts`);
  }

  getServiceStatuses(): Observable<ServiceStatus[]> {
    return this.http.get<ServiceStatus[]>(`${this.api}/status`);
  }

  /**
   * Envoie un fichier au moteur d'analyse et retourne le verdict enregistré.
   *
   * Le fichier transite en multipart sous le nom de champ `file`, celui attendu
   * par le paramètre `@RequestParam("file")` du ScanController. La réponse est
   * l'analyse telle qu'elle vient d'être écrite en base : elle porte donc déjà
   * son identifiant et son horodatage définitifs.
   */
  /**
   * Justification archivée d'une analyse : contribution de chaque groupe de
   * caractéristiques au verdict, de la plus déterminante à la moins.
   *
   * Les contributions sont enregistrées au moment de l'analyse, la base ne
   * conservant que l'empreinte du fichier et non son contenu. Une analyse
   * antérieure à cette fonctionnalité renverra donc une liste vide.
   */
  getContributions(scanId: string): Observable<Contribution[]> {
    return this.http.get<Contribution[]>(`${this.api}/scans/${scanId}/contributions`);
  }

  /**
   * Enregistre l'appréciation d'un analyste sur un verdict.
   *
   * L'avis se superpose au verdict du moteur sans le remplacer. C'est aussi la
   * seule façon de mesurer le taux de faux positifs constaté en exploitation,
   * par opposition à celui mesuré sur le jeu de test.
   */
  enregistrerAvis(
    scanId: string,
    avis: 'CONFIRME' | 'FAUX_POSITIF' | 'TRAITE',
    commentaire?: string,
    criticite?: 'CRITICAL' | 'HIGH' | 'MEDIUM' | null,
  ): Observable<unknown> {
    return this.http.post(`${this.api}/scans/${scanId}/avis`, {
      avis,
      commentaire: commentaire ?? null,
      criticite: criticite ?? null,
    });
  }

  scanFile(file: File): Observable<ScanRecord> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<ScanRecord>(`${this.api}/scan`, body);
  }
}
