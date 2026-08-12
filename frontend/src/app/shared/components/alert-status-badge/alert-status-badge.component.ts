import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AlertStatus } from '../../../core/models/guardian.models';

const COLOR: Record<AlertStatus, string> = {
  OPEN: '#FF4444',
  INVESTIGATING: '#FFB800',
  RESOLVED: '#00FF88',
};

/**
 * Libellés affichés.
 *
 * Les valeurs restent en anglais dans le contrat d'API et en base : les traduire
 * là obligerait à modifier les données à chaque évolution de la langue de
 * l'interface. La traduction se fait au seul endroit qui la concerne, l'affichage.
 */
const LIBELLE: Record<AlertStatus, string> = {
  OPEN: 'Ouverte',
  INVESTIGATING: 'En cours',
  RESOLVED: 'Clôturée',
};

@Component({
  selector: 'gd-alert-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      class="inline-flex items-center font-mono text-[10px] tracking-[0.08em] uppercase px-2 py-1 rounded-sm border"
      [style.color]="color"
      [style.borderColor]="color + '4D'"
      [style.background]="color + '14'"
    >
      {{ libelle }}
    </span>
  `,
})
export class AlertStatusBadgeComponent {
  @Input() status: AlertStatus = 'OPEN';

  get color(): string {
    return COLOR[this.status];
  }

  get libelle(): string {
    return LIBELLE[this.status] ?? this.status;
  }
}
