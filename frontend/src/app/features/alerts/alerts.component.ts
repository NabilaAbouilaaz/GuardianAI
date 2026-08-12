import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { AlertRecord, Contribution } from '../../core/models/guardian.models';
import { largeur, niveau, resume } from '../../core/explication';
import { SeverityBadgeComponent } from '../../shared/components/severity-badge/severity-badge.component';
import { AlertStatusBadgeComponent } from '../../shared/components/alert-status-badge/alert-status-badge.component';

@Component({
  selector: 'gd-alerts',
  standalone: true,
  imports: [CommonModule, FormsModule, SeverityBadgeComponent, AlertStatusBadgeComponent],
  templateUrl: './alerts.component.html',
})
export class AlertsComponent implements OnInit {
  alerts: AlertRecord[] = [];
  selectedId: string | null = null;

  /** Contributions réelles de l'alerte sélectionnée, calculées par SHAP. */
  contributions: Contribution[] = [];
  contributionsIndisponibles = false;
  /** Affiche les valeurs SHAP brutes, masquees par defaut. */
  detailsOuverts = false;

  /**
   * Recommandations de remédiation.
   *
   * Ce sont des actions standard de réponse à incident, communes à toute
   * détection critique. Elles ne résultent d'aucune analyse du fichier et sont
   * présentées comme telles dans l'interface, pour ne pas les faire passer pour
   * un résultat du moteur.
   */
  readonly remediation: string[] = [
    "Isoler la machine concernée du réseau.",
    "Vérifier les partages réseau montés depuis ce poste.",
    "Renouveler les identifiants accessibles depuis cette machine.",
    "Conserver une copie du fichier et des journaux avant toute remédiation.",
    "Signaler l'incident au responsable sécurité.",
  ];

  error: string | null = null;

  constructor(
    private readonly data: GuardianDataService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.data.getAlerts().subscribe({
      next: (v) => {
        this.alerts = v;
        this.error = null;
        if (v.length) {
          this.select(v[0].id);
        }
        this.cdr.detectChanges();
      },
      error: (e: HttpErrorResponse) => {
        this.error = e.status === 0
          ? "Backend injoignable. Verifier qu'il est demarre sur le port 8080."
          : `Le serveur a repondu une erreur (HTTP ${e.status}).`;
        this.cdr.detectChanges();
      },
    });
  }

  select(id: string): void {
    this.selectedId = id;
    this.contributions = [];
    this.contributionsIndisponibles = false;
    this.avisErreur = null;

    const alerte = this.alerts.find((a) => a.id === id);
    if (!alerte) {
      return;
    }

    // On repart de la justification déjà enregistrée : l'analyste doit pouvoir
    // relire et corriger son propre commentaire, pas repartir d'une page blanche.
    this.commentaire = alerte.commentaire ?? '';
    this.criticiteRetenue = alerte.criticiteAjustee ? alerte.severity : null;

    this.data.getContributions(alerte.scanId).subscribe({
      next: (c) => {
        this.contributions = c;
        // Les analyses anterieures a la mise en place de SHAP n'ont pas de
        // justification enregistree : le fichier n'ayant pas ete conserve, elle
        // ne peut pas etre recalculee. Il faut le dire, pas afficher un vide.
        this.contributionsIndisponibles = c.length === 0;
        this.cdr.detectChanges();
      },
      error: () => {
        this.contributionsIndisponibles = true;
        this.cdr.detectChanges();
      },
    });
  }

  get selected(): AlertRecord | undefined {
    return this.alerts.find((a) => a.id === this.selectedId);
  }

  /** Avis en cours d'envoi, pour désactiver les boutons pendant l'appel. */
  avisEnCours = false;
  avisErreur: string | null = null;

  /** Justification saisie par l'analyste, et criticité qu'il retient. */
  commentaire = '';
  criticiteRetenue: 'CRITICAL' | 'HIGH' | 'MEDIUM' | null = null;

  /**
   * Transmet l'appréciation de l'analyste et rafraîchit la liste.
   *
   * On recharge plutôt que de modifier l'alerte localement : le statut affiché
   * est déduit de l'avis côté serveur, et le recalculer ici dupliquerait une
   * règle qui pourrait diverger.
   */
  donnerAvis(avis: 'CONFIRME' | 'FAUX_POSITIF' | 'TRAITE'): void {
    const alerte = this.selected;
    if (!alerte || this.avisEnCours) {
      return;
    }

    // Contredire la mesure du moteur sans dire pourquoi n'apporte rien : ni à
    // l'escalade, ni à l'amélioration des règles de détection. Le serveur
    // applique la même exigence.
    if (avis === 'FAUX_POSITIF' && !this.commentaire.trim()) {
      this.avisErreur = 'Expliquez pourquoi ce fichier est sain avant de le déclarer faux positif.';
      return;
    }

    this.avisEnCours = true;
    this.avisErreur = null;

    this.data.enregistrerAvis(
      alerte.scanId, avis, this.commentaire.trim(), this.criticiteRetenue,
    ).subscribe({
      next: () => {
        this.avisEnCours = false;
        const idCourant = this.selectedId;
        this.data.getAlerts().subscribe({
          next: (v) => {
            this.alerts = v;
            if (idCourant && v.some((a) => a.id === idCourant)) {
              this.selectedId = idCourant;
            }
            this.cdr.detectChanges();
          },
          error: () => this.cdr.detectChanges(),
        });
      },
      error: (e: HttpErrorResponse) => {
        this.avisEnCours = false;
        this.avisErreur = e.error?.erreur ?? "L'avis n'a pas pu être enregistré.";
        this.cdr.detectChanges();
      },
    });
  }

  largeur(c: Contribution): number {
    return largeur(c, this.contributions);
  }

  niveau(c: Contribution): string {
    return niveau(c, this.contributions);
  }

  /**
   * Phrase de synthese. Une alerte n'existe que pour un verdict non benin ;
   * la severite critique correspond a un verdict malveillant, les autres a
   * un fichier juge suspect.
   */
  get resumeAlerte(): string {
    const statut = this.selected?.severity === 'CRITICAL' ? 'MALICIOUS' : 'SUSPICIOUS';
    return resume(this.contributions, statut);
  }
}
