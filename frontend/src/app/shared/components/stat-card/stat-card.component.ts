import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'gd-stat-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-[#0C1220] border border-white/[0.07] rounded p-5">
      <div class="font-mono text-[11px] tracking-[0.1em] uppercase text-[#5A7A9A] mb-2">
        {{ label }}
      </div>
      <div class="font-mono text-[26px] font-bold" [style.color]="color ?? '#E2EBF5'">
        {{ value }}
      </div>
      <div *ngIf="hint" class="font-mono text-[11px] text-[#5A7A9A] mt-1">{{ hint }}</div>
    </div>
  `,
})
export class StatCardComponent {
  @Input() label = '';
  @Input() value: string | number = '';
  @Input() hint?: string;
  @Input() color?: string;
}
