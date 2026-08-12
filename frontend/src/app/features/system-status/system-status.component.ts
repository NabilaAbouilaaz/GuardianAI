import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { ServiceStatus } from '../../core/models/guardian.models';

@Component({
  selector: 'gd-system-status',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './system-status.component.html',
})
export class SystemStatusComponent implements OnInit {
  services: ServiceStatus[] = [];

  /**
   * Cet ecran ne depend pas du contenu de la base : il doit toujours lister des
   * services. Une liste vide signale donc forcement une anomalie, qu'il faut
   * afficher plutot que de laisser un tableau muet.
   */
  error: string | null = null;

  constructor(
    private readonly data: GuardianDataService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.data.getServiceStatuses().subscribe({
      next: (v) => {
        this.services = v;
        this.error = null;
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

  statusColor(s: ServiceStatus['status']): string {
    return { OPERATIONAL: '#00FF88', DEGRADED: '#FFB800', DOWN: '#FF4444' }[s];
  }

  /** Libellé français. La constante reste en anglais dans le contrat d'API. */
  statusLibelle(s: ServiceStatus['status']): string {
    return { OPERATIONAL: 'Opérationnel', DEGRADED: 'Dégradé', DOWN: 'Hors service' }[s] ?? s;
  }
}
