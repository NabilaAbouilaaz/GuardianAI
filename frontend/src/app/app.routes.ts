import { Routes } from '@angular/router';

/**
 * Ajouter une nouvelle vue = ajouter une ligne ici + créer le dossier
 * correspondant sous src/app/features/. Aucun autre fichier à toucher.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'scan',
    loadComponent: () => import('./features/scan/scan.component').then((m) => m.ScanComponent),
  },
  {
    path: 'alerts',
    loadComponent: () =>
      import('./features/alerts/alerts.component').then((m) => m.AlertsComponent),
  },
  {
    path: 'system-status',
    loadComponent: () =>
      import('./features/system-status/system-status.component').then(
        (m) => m.SystemStatusComponent,
      ),
  },
  { path: '**', redirectTo: 'dashboard' },
];
