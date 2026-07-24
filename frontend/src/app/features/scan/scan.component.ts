import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { GuardianDataService } from '../../core/services/guardian-data.service';
import { ScanStatus } from '../../core/models/guardian.models';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';

interface QueuedFile {
  file: File;
  scanning: boolean;
  result?: ScanStatus;
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

  readonly pipeline = [
    { step: '01', label: 'Static Feature Extraction', desc: 'PE headers, entropy, imports, DLL analysis' },
    { step: '02', label: 'Behavioral Sandbox', desc: 'API call sequences, registry ops, network I/O' },
    { step: '03', label: 'ML Inference', desc: 'XGBoost + LSTM ensemble with confidence score' },
    { step: '04', label: 'SHAP Explanation', desc: 'Feature importance & decision justification' },
  ];

  constructor(private readonly data: GuardianDataService) {}

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
    this.data.simulateScan(item.file.name).subscribe(({ status }) => {
      item.scanning = false;
      item.result = status;
    });
  }

  scanAll(): void {
    this.queue.filter((i) => !i.result && !i.scanning).forEach((i) => this.scanFile(i));
  }

  sizeMb(file: File): string {
    return (file.size / 1024 / 1024).toFixed(2);
  }
}
