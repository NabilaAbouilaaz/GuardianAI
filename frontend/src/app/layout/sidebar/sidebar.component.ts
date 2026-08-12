import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';

interface NavEntry {
  label: string;
  route: string;
  icon: IconName;
  /** Réservée aux administrateurs. */
  admin?: boolean;
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
          *ngFor="let item of entreesVisibles"
          [routerLink]="item.route"
          routerLinkActive="bg-[#00FF88]/[0.06] border-l-2 border-l-[#00FF88] text-[#00FF88]"
          class="flex items-center gap-3 px-5 py-3 text-[13px] font-mono text-[#5A7A9A] border-l-2 border-l-transparent hover:bg-white/[0.03] transition-colors"
          #rla="routerLinkActive"
        >
          <gd-icon [name]="item.icon" [active]="rla.isActive"></gd-icon>
          {{ item.label }}
        </a>
      </nav>

      <!--
        Utilisateur connecté. Savoir sous quelle identité on agit est une
        exigence de base dans un outil dont chaque action est tracée (RF-11) :
        l'analyste doit pouvoir vérifier d'un coup d'œil que ses analyses lui
        seront bien attribuées.
      -->
      <div *ngIf="auth.utilisateur() as u" class="border-t border-white/[0.07] px-5 py-4">
        <div class="font-sans text-[13px] text-[#E2EBF5] mb-0.5">{{ u.nom }}</div>
        <div class="font-mono text-[10px] tracking-[0.06em] text-[#5A7A9A] mb-3">
          {{ u.role === 'ADMINISTRATEUR' ? 'Administrateur' : 'Analyste' }}
        </div>
        <button
          (click)="auth.deconnexion()"
          class="w-full font-mono text-[10px] tracking-[0.08em] text-[#5A7A9A] bg-transparent border border-white/[0.1] px-3 py-2 rounded-sm cursor-pointer hover:text-[#FF4444] hover:border-[#FF4444]/40 transition-colors"
        >
          SE DÉCONNECTER
        </button>
      </div>
    </aside>
  `,
})
export class SidebarComponent {
  readonly auth = inject(AuthService);

  // Libellés en français, comme le reste de l'interface. Les chemins d'URL
  // restent en anglais : les changer casserait les liens déjà partagés, sans
  // rien apporter à l'utilisateur qui ne les lit pas.
  nav: NavEntry[] = [
    { label: 'Tableau de bord', route: '/dashboard', icon: 'dashboard' },
    { label: 'Analyse', route: '/scan', icon: 'scan' },
    { label: 'Alertes', route: '/alerts', icon: 'alert' },
    { label: 'État des services', route: '/system-status', icon: 'settings' },
    { label: 'Comptes', route: '/utilisateurs', icon: 'settings', admin: true },
  ];

  /**
   * Entrées réellement accessibles à l'utilisateur connecté.
   *
   * Masquer une entrée qu'un analyste ne peut pas ouvrir vaut mieux que de la
   * proposer pour la refuser ensuite : un menu doit refléter ce qui est possible.
   */
  get entreesVisibles(): NavEntry[] {
    return this.nav.filter((e) => !e.admin || this.auth.estAdministrateur());
  }
}
