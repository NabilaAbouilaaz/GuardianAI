import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

@Component({
  selector: 'gd-login',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';

  enCours = false;
  erreur: string | null = null;

  /** Message venant d'une déconnexion ou d'une session expirée. */
  avis: string | null = null;

  private retour = '/dashboard';

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.avis = params.get('motif');
    this.retour = params.get('retour') ?? '/dashboard';

    // Session encore valide : inutile de redemander des identifiants.
    if (this.auth.connecte()) {
      this.router.navigateByUrl(this.retour);
    }
  }

  valider(): void {
    if (!this.username.trim() || !this.password) {
      this.erreur = 'Identifiant et mot de passe sont requis.';
      return;
    }

    this.enCours = true;
    this.erreur = null;
    this.avis = null;

    this.auth.connexion(this.username.trim(), this.password).subscribe({
      next: () => {
        this.enCours = false;
        // Mot de passe initial : le renouvellement passe avant toute chose.
        this.router.navigateByUrl(
          this.auth.changementRequis() ? '/changer-mot-de-passe' : this.retour,
        );
      },
      error: (e: HttpErrorResponse) => {
        this.enCours = false;
        this.password = '';
        this.erreur = this.message(e);
        this.cdr.detectChanges();
      },
    });
  }

  /**
   * Le serveur répond volontairement la même chose pour un identifiant inconnu
   * et un mot de passe erroné : préciser lequel des deux est faux permettrait
   * d'énumérer les comptes existants. On conserve cette indistinction ici.
   */
  private message(e: HttpErrorResponse): string {
    if (e.status === 0) {
      return "Backend injoignable. Vérifier qu'il est démarré sur le port 8080.";
    }
    if (e.status === 401) {
      return e.error?.erreur ?? 'Identifiant ou mot de passe incorrect.';
    }
    if (e.status === 429) {
      // Compte bloqué après plusieurs échecs : le serveur précise le délai.
      return e.error?.erreur ?? 'Trop de tentatives. Compte temporairement bloqué.';
    }
    return `Erreur inattendue (HTTP ${e.status}).`;
  }
}
