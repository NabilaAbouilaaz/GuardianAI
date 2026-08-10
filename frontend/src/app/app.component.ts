import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { SidebarComponent } from './layout/sidebar/sidebar.component';

@Component({
  selector: 'gd-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, SidebarComponent],
  template: `
    <!--
      La barre latérale n'apparaît que lorsque la navigation est réellement
      possible : ni sur l'écran de connexion, ni tant qu'un renouvellement de mot
      de passe est imposé. Proposer des liens que la garde refusera serait
      incohérent, et laisserait croire à un dysfonctionnement.
    -->
    <div class="flex min-h-screen bg-[#070B12] text-[#E2EBF5]">
      <gd-sidebar *ngIf="navigable"></gd-sidebar>
      <main class="flex-1 overflow-auto" [class.p-8]="navigable">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);

  /** Vrai lorsque l'utilisateur peut circuler entre les vues métier. */
  get navigable(): boolean {
    return this.auth.connecte() && !this.auth.changementRequis();
  }

  ngOnInit(): void {
    // Une session restaurée depuis le navigateur doit être confrontée au serveur :
    // le jeton peut avoir expiré, ou un changement de mot de passe être devenu
    // obligatoire depuis la dernière connexion.
    this.auth.revaliderSession();
  }
}
