import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { AlertStatus } from '../../../core/models/guardian.models';

const COLOR: Record<AlertStatus, string> = {
  OPEN: '#FF4444',
  INVESTIGATING: '#FFB800',
  RESOLVED: '#00FF88',
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
      {{ status }}
    </span>
  `,
})
export class AlertStatusBadgeComponent {
  @Input() status: AlertStatus = 'OPEN';

  get color(): string {
    return COLOR[this.status];
  }
}
