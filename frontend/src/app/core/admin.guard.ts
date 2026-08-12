import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

/**
 * Réserve une vue aux administrateurs.
 *
 * Comme `authGuard`, c'est un confort d'affichage : la protection réelle est
 * côté serveur, où `/api/v1/utilisateurs/**` exige le rôle ADMINISTRATEUR.
 * Ici, on évite simplement de proposer un écran qui ne renverrait que des 403.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.connecte()) {
    return router.createUrlTree(['/connexion']);
  }
  if (!auth.estAdministrateur()) {
    return router.createUrlTree(['/dashboard']);
  }
  return true;
};
