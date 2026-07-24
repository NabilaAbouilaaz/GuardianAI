import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { STATUS_COLOR, ScanStatus } from '../../../core/models/guardian.models';

@Component({
  selector: 'gd-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      class="inline-flex items-center gap-1.5 font-mono text-[10px] tracking-[0.08em] uppercase px-2 py-1 rounded-sm border"
      [style.color]="color"
      [style.borderColor]="color + '4D'"
      [style.background]="color + '14'"
    >
      <span class="w-1.5 h-1.5 rounded-full" [style.background]="color"></span>
      {{ status }}
    </span>
  `,
})
export class StatusBadgeComponent {
  @Input() status: ScanStatus = 'CLEAN';

  get color(): string {
    return STATUS_COLOR[this.status];
  }
}
