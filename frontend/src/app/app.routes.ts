import { Routes } from '@angular/router';
import { adminGuard } from './core/admin.guard';
import { authGuard } from './core/auth.guard';

/**
 * Ajouter une nouvelle vue = ajouter une ligne ici + créer le dossier
 * correspondant sous src/app/features/. Aucun autre fichier à toucher.
 *
 * Toutes les vues métier sont protégées par `authGuard` (exigence RF-07).
 * Seule la page de connexion reste accessible sans session.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'connexion',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'changer-mot-de-passe',
    loadComponent: () =>
      import('./features/login/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'scan',
    canActivate: [authGuard],
    loadComponent: () => import('./features/scan/scan.component').then((m) => m.ScanComponent),
  },
  {
    path: 'alerts',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/alerts/alerts.component').then((m) => m.AlertsComponent),
  },
  {
    path: 'system-status',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/system-status/system-status.component').then(
        (m) => m.SystemStatusComponent,
      ),
  },
  {
    // Réservée aux administrateurs, en plus de l'authentification.
    path: 'utilisateurs',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/utilisateurs/utilisateurs.component').then(
        (m) => m.UtilisateursComponent,
      ),
  },
  { path: '**', redirectTo: 'dashboard' },
];
