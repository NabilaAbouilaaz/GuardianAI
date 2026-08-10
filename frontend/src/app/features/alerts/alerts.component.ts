import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { AlertRecord, Contribution } from '../../core/models/guardian.models';
import { largeur, niveau, resume } from '../../core/explication';
import { SeverityBadgeComponent } from '../../shared/components/severity-badge/severity-badge.component';
import { AlertStatusBadgeComponent } from '../../shared/components/alert-status-badge/alert-status-badge.component';

@Component({
  selector: 'gd-alerts',
  standalone: true,
  imports: [CommonModule, SeverityBadgeComponent, AlertStatusBadgeComponent],
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

    const alerte = this.alerts.find((a) => a.id === id);
    if (!alerte) {
      return;
    }

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
