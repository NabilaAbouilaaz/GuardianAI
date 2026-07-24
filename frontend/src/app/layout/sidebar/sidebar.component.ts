import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';

interface NavEntry {
  label: string;
  route: string;
  icon: IconName;
}

@Component({
  selector: 'gd-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <aside class="w-[220px] shrink-0 bg-[#0A1018] border-r border-white/[0.07] flex flex-col">
      <div class="flex items-center gap-2 px-5 py-5 border-b border-white/[0.07]">
        <gd-icon name="shield"></gd-icon>
        <span class="font-mono text-sm font-semibold text-[#E2EBF5]">GuardianAI</span>
      </div>

      <nav class="flex-1 py-3">
        <a
          *ngFor="let item of nav"
          [routerLink]="item.route"
          routerLinkActive="bg-[#00FF88]/[0.06] border-l-2 border-l-[#00FF88] text-[#00FF88]"
          class="flex items-center gap-3 px-5 py-3 text-[13px] font-mono text-[#5A7A9A] border-l-2 border-l-transparent hover:bg-white/[0.03] transition-colors"
          #rla="routerLinkActive"
        >
          <gd-icon [name]="item.icon" [active]="rla.isActive"></gd-icon>
          {{ item.label }}
        </a>
      </nav>
    </aside>
  `,
})
export class SidebarComponent {
  nav: NavEntry[] = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'Scan', route: '/scan', icon: 'scan' },
    { label: 'Alerts', route: '/alerts', icon: 'alert' },
    { label: 'System Status', route: '/system-status', icon: 'settings' },
  ];
}
