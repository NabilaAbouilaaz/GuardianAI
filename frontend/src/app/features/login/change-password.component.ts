import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Renouvellement du mot de passe initial.
 *
 * Imposé à la première connexion : un mot de passe transmis par un tiers, et
 * documenté dans le dépôt, ne doit rester valide que le temps du premier accès.
 */
@Component({
  selector: 'gd-change-password',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './change-password.component.html',
})
export class ChangePasswordComponent {
  ancien = '';
  nouveau = '';
  confirmation = '';

  enCours = false;
  erreur: string | null = null;
  regles: string[] = [];

  constructor(
    readonly auth: AuthService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  /** Contrôle local immédiat, doublé côté serveur qui reste seul juge. */
  get problemesLocaux(): string[] {
    const p: string[] = [];
    if (this.nouveau && this.nouveau.length < 12) {
      p.push('Au moins 12 caractères.');
    }
    if (this.nouveau && !/[0-9]/.test(this.nouveau)) {
      p.push('Au moins un chiffre.');
    }
    if (this.nouveau && !/[a-zA-Z]/.test(this.nouveau)) {
      p.push('Au moins une lettre.');
    }
    if (this.confirmation && this.nouveau !== this.confirmation) {
      p.push('Les deux saisies diffèrent.');
    }
    return p;
  }

  get pretAValider(): boolean {
    return (
      !!this.ancien &&
      !!this.nouveau &&
      !!this.confirmation &&
      this.problemesLocaux.length === 0
    );
  }

  valider(): void {
    if (!this.pretAValider) {
      return;
    }

    this.enCours = true;
    this.erreur = null;
    this.regles = [];

    this.auth.changerMotDePasse(this.ancien, this.nouveau).subscribe({
      next: () => {
        this.enCours = false;
        this.router.navigateByUrl('/dashboard');
      },
      error: (e: HttpErrorResponse) => {
        this.enCours = false;
        this.erreur = e.error?.erreur ?? `Erreur inattendue (HTTP ${e.status}).`;
        this.regles = e.error?.regles ?? [];
        this.cdr.detectChanges();
      },
    });
  }
}
