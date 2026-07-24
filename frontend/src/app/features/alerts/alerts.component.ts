import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { AlertRecord, ShapFeature } from '../../core/models/guardian.models';
import { SeverityBadgeComponent } from '../../shared/components/severity-badge/severity-badge.component';
import { AlertStatusBadgeComponent } from '../../shared/components/alert-status-badge/alert-status-badge.component';

@Component({
  selector: 'gd-alerts',
  standalone: true,
  imports: [CommonModule, SeverityBadgeComponent, AlertStatusBadgeComponent],
  templateUrl: './alerts.component.html',
})
export class AlertsComponent implements OnInit {
  alerts: AlertRecord[] = [];
  selectedId: string | null = null;

  // TODO(amélioration facile): brancher sur GuardianDataService.getShapFeatures(alertId)
  // et GuardianDataService.getRemediation(alertId) quand le backend IA sera prêt.
  readonly shapFeatures: ShapFeature[] = [
    { feature: 'Mass file encryption loop', score: 0.94, direction: 'malicious' },
    { feature: 'Shadow copy deletion (VSS)', score: 0.88, direction: 'malicious' },
    { feature: 'Wallpaper modification via registry', score: 0.76, direction: 'malicious' },
    { feature: 'C2 beacon pattern (TLS 1.2)', score: 0.71, direction: 'malicious' },
    { feature: 'Valid PE signature present', score: 0.32, direction: 'benign' },
  ];

  readonly remediation: string[] = [
    'Immediately quarantine the host machine from the network.',
    'Isolate all shared drives mounted by the affected endpoint.',
    'Revoke and rotate all credentials accessible from this machine.',
    'Initiate forensic image of the disk before remediation.',
    'Report incident to SOC lead within 1 hour (SLA: critical).',
  ];

  constructor(private readonly data: GuardianDataService) {}

  ngOnInit(): void {
    this.data.getAlerts().subscribe((v) => {
      this.alerts = v;
      this.selectedId = v[0]?.id ?? null;
    });
  }

  select(id: string): void {
    this.selectedId = id;
  }

  get selected(): AlertRecord | undefined {
    return this.alerts.find((a) => a.id === this.selectedId);
  }
}
