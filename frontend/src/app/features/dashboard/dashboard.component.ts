import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
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

  constructor(private readonly data: GuardianDataService) {}

  ngOnInit(): void {
    this.data.getTrend().subscribe((v) => (this.trend = v));
    this.data.getFileTypeBreakdown().subscribe((v) => (this.fileTypes = v));
    this.data.getRecentScans().subscribe((v) => (this.recentScans = v));
  }

  get totalMalicious(): number {
    return this.recentScans.filter((s) => s.status === 'MALICIOUS').length;
  }

  get totalSuspicious(): number {
    return this.recentScans.filter((s) => s.status === 'SUSPICIOUS').length;
  }

  get totalClean(): number {
    return this.recentScans.filter((s) => s.status === 'CLEAN').length;
  }

  maxTypeCount(): number {
    return Math.max(...this.fileTypes.map((t) => t.count), 1);
  }
}
