import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CompteCree,
  Utilisateur,
  UtilisateursService,
} from '../../core/services/utilisateurs.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'gd-utilisateurs',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './utilisateurs.component.html',
})
export class UtilisateursComponent implements OnInit {
  utilisateurs: Utilisateur[] = [];
  erreur: string | null = null;
  enCours = false;

  // Formulaire de création
  formulaireOuvert = false;
  nouvelUsername = '';
  nouveauNom = '';
  nouveauRole: 'ANALYSTE' | 'ADMINISTRATEUR' = 'ANALYSTE';

  /**
   * Compte tout juste créé ou réinitialisé, avec son mot de passe.
   *
   * Affiché jusqu'à ce que l'administrateur le referme explicitement : ce mot de
   * passe n'existe nulle part ailleurs et ne pourra pas être réaffiché. Le
   * masquer automatiquement ferait perdre l'accès au compte.
   */
  compteCree: CompteCree | null = null;

  constructor(
    private readonly service: UtilisateursService,
    readonly auth: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.lister().subscribe({
      next: (v) => {
        this.utilisateurs = v;
        this.erreur = null;
        this.cdr.detectChanges();
      },
      error: (e: HttpErrorResponse) => {
        this.erreur = this.message(e);
        this.cdr.detectChanges();
      },
    });
  }

  creer(): void {
    if (!this.nouvelUsername.trim() || this.enCours) {
      return;
    }

    this.enCours = true;
    this.erreur = null;

    this.service
      .creer(this.nouvelUsername.trim(), this.nouveauNom.trim(), this.nouveauRole)
      .subscribe({
        next: (compte) => {
          this.enCours = false;
          this.compteCree = compte;
          this.formulaireOuvert = false;
          this.nouvelUsername = '';
          this.nouveauNom = '';
          this.nouveauRole = 'ANALYSTE';
          this.charger();
        },
        error: (e: HttpErrorResponse) => {
          this.enCours = false;
          this.erreur = this.message(e);
          this.cdr.detectChanges();
        },
      });
  }

  basculerActivation(u: Utilisateur): void {
    if (this.enCours) {
      return;
    }
    this.enCours = true;
    this.erreur = null;

    this.service.changerActivation(u.id, !u.actif).subscribe({
      next: () => {
        this.enCours = false;
        this.charger();
      },
      error: (e: HttpErrorResponse) => {
        this.enCours = false;
        this.erreur = this.message(e);
        this.cdr.detectChanges();
      },
    });
  }

  reinitialiser(u: Utilisateur): void {
    if (this.enCours) {
      return;
    }
    this.enCours = true;
    this.erreur = null;

    this.service.reinitialiser(u.id).subscribe({
      next: (compte) => {
        this.enCours = false;
        this.compteCree = compte;
        this.charger();
      },
      error: (e: HttpErrorResponse) => {
        this.enCours = false;
        this.erreur = this.message(e);
        this.cdr.detectChanges();
      },
    });
  }

  /** Vrai pour le compte de l'utilisateur connecté : certaines actions lui sont interdites. */
  estMoi(u: Utilisateur): boolean {
    return u.username === this.auth.utilisateur()?.username;
  }

  private message(e: HttpErrorResponse): string {
    if (e.status === 0) {
      return "Backend injoignable. Vérifier qu'il est démarré sur le port 8080.";
    }
    if (e.status === 403) {
      return "Cette opération est réservée aux administrateurs.";
    }
    return e.error?.erreur ?? `Erreur inattendue (HTTP ${e.status}).`;
  }
}
