import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
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

  constructor(private readonly data: GuardianDataService) {}

  ngOnInit(): void {
    this.data.getServiceStatuses().subscribe((v) => (this.services = v));
  }

  statusColor(s: ServiceStatus['status']): string {
    return { OPERATIONAL: '#00FF88', DEGRADED: '#FFB800', DOWN: '#FF4444' }[s];
  }
}
