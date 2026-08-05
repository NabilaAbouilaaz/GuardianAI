import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { FileTypeCount, ScanRecord, TrendPoint } from '../../core/models/guardian.models';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'gd-dashboard',
  standalone: true,
  imports: [CommonModule, StatCardComponent, StatusBadgeComponent],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  trend: TrendPoint[] = [];
  fileTypes: FileTypeCount[] = [];
  recentScans: ScanRecord[] = [];

  /** Message affiché lorsque l'API est injoignable, pour ne pas confondre avec une base vide. */
  error: string | null = null;

  constructor(
    private readonly data: GuardianDataService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.data.getTrend().subscribe({
      next: (v) => {
        this.trend = v;
        this.cdr.detectChanges();
      },
      error: (e) => this.signaler(e),
    });

    this.data.getFileTypeBreakdown().subscribe({
      next: (v) => {
        this.fileTypes = v;
        this.cdr.detectChanges();
      },
      error: (e) => this.signaler(e),
    });

    this.data.getRecentScans().subscribe({
      next: (v) => {
        this.recentScans = v;
        this.cdr.detectChanges();
      },
      error: (e) => this.signaler(e),
    });
  }

  /**
   * Les compteurs portent la mention (7d) : ils doivent donc s'appuyer sur la
   * tendance hebdomadaire, et non sur `recentScans` qui ne renvoie que les vingt
   * dernieres analyses quelle que soit leur date.
   */
  get totalMalicious(): number {
    return this.trend.reduce((somme, jour) => somme + jour.malicious, 0);
  }

  get totalSuspicious(): number {
    return this.trend.reduce((somme, jour) => somme + jour.suspicious, 0);
  }

  get totalClean(): number {
    return this.trend.reduce((somme, jour) => somme + jour.clean, 0);
  }

  get totalScans(): number {
    return this.totalMalicious + this.totalSuspicious + this.totalClean;
  }

  maxTypeCount(): number {
    return Math.max(...this.fileTypes.map((t) => t.count), 1);
  }

  private signaler(e: HttpErrorResponse): void {
    this.error = e.status === 0
      ? "Backend injoignable. Verifier qu'il est demarre sur le port 8080."
      : `Le serveur a repondu une erreur (HTTP ${e.status}).`;
    this.cdr.detectChanges();
  }
}
