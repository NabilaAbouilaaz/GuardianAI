import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AlertSeverity, SEVERITY_COLOR } from '../../../core/models/guardian.models';

/** Libellés affichés. Les constantes restent en anglais côté API et base. */
const LIBELLE: Record<AlertSeverity, string> = {
  CRITICAL: 'Critique',
  HIGH: 'Élevée',
  MEDIUM: 'Moyenne',
};

@Component({
  selector: 'gd-severity-badge',
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
export class SeverityBadgeComponent {
  @Input() severity: AlertSeverity = 'MEDIUM';

  get color(): string {
    return SEVERITY_COLOR[this.severity];
  }

  get libelle(): string {
    return LIBELLE[this.severity] ?? this.severity;
  }
}
