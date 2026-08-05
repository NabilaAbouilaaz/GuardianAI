import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component } from '@angular/core';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { ScanRecord, ScanStatus } from '../../core/models/guardian.models';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';

interface QueuedFile {
  file: File;
  scanning: boolean;
  result?: ScanStatus;
  /** Analyse complète retournée par le backend, utilisée pour le détail affiché. */
  record?: ScanRecord;
  /** Message d'erreur lisible lorsque l'analyse n'a pas abouti. */
  error?: string;
}

@Component({
  selector: 'gd-scan',
  standalone: true,
  imports: [CommonModule, StatusBadgeComponent, IconComponent],
  templateUrl: './scan.component.html',
})
export class ScanComponent {
  dragging = false;
  queue: QueuedFile[] = [];

  /**
   * Étapes réellement exécutées par le moteur d'analyse.
   *
   * L'analyse est entièrement statique : le fichier n'est jamais exécuté, ce qui
   * évite d'avoir à isoler un environnement d'exécution et garantit un temps de
   * réponse compatible avec l'exigence RNF-01.
   */
  readonly pipeline = [
    { step: '01', label: 'Extraction statique', desc: 'En-têtes PE, entropie des sections, imports, chaînes' },
    { step: '02', label: 'Vectorisation EMBER2024', desc: 'Conversion en vecteur de caractéristiques normalisé' },
    { step: '03', label: 'Inférence LightGBM', desc: 'Score de malveillance et seuil de décision calibré' },
    { step: '04', label: 'Verdict et traçabilité', desc: 'Classification, empreinte SHA-256, enregistrement en base' },
  ];

  /**
   * Le rendu est declenche explicitement apres chaque reponse HTTP.
   *
   * Angular s'en charge normalement seul via zone.js, mais certaines extensions
   * de navigateur remplacent XMLHttpRequest et empechent cette interception : le
   * resultat arrivait alors sans que la vue soit redessinee, et le fichier restait
   * affiche en "analyse en cours" jusqu'au clic suivant.
   */
  constructor(
    private readonly data: GuardianDataService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  onDragOver(e: DragEvent): void {
    e.preventDefault();
    this.dragging = true;
  }

  onDragLeave(): void {
    this.dragging = false;
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.dragging = false;
    const dropped = Array.from(e.dataTransfer?.files ?? []);
    this.addFiles(dropped);
  }

  onFileInput(e: Event): void {
    const input = e.target as HTMLInputElement;
    if (input.files) this.addFiles(Array.from(input.files));
  }

  addFiles(files: File[]): void {
    this.queue.push(...files.map((file) => ({ file, scanning: false })));
  }

  scanFile(item: QueuedFile): void {
    item.scanning = true;
    item.error = undefined;

    this.data.scanFile(item.file).subscribe({
      next: (record) => {
        item.scanning = false;
        item.record = record;
        item.result = record.status;
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        item.scanning = false;
        item.error = this.messageErreur(err);
        this.cdr.detectChanges();
      },
    });
  }

  scanAll(): void {
    this.queue.filter((i) => !i.result && !i.scanning).forEach((i) => this.scanFile(i));
  }

  sizeMb(file: File): string {
    return (file.size / 1024 / 1024).toFixed(2);
  }

  /**
   * Traduit les erreurs du backend en messages compréhensibles par un analyste.
   *
   * Les codes 422 et 503 sont produits explicitement par le ScanController ;
   * le statut 0 correspond à un backend injoignable, cas fréquent en développement
   * lorsque le terminal Spring Boot n'a pas été lancé.
   */
  private messageErreur(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return "Backend injoignable. Vérifier qu'il est démarré sur le port 8080.";
    }
    if (err.status === 422) {
      return err.error?.erreur ?? "Format de fichier non pris en charge par le moteur.";
    }
    if (err.status === 503) {
      return err.error?.erreur ?? "Moteur d'analyse indisponible. Réessayer dans un instant.";
    }
    return err.error?.message ?? `Erreur inattendue (HTTP ${err.status}).`;
  }
}
