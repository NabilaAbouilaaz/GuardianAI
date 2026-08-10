import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

/**
 * Interdit l'accès aux vues métier sans session ouverte.
 *
 * C'est une garde de confort, pas une mesure de sécurité : rien n'empêche de
 * contourner du code exécuté dans le navigateur. La protection réelle est côté
 * serveur, où chaque appel exige un jeton signé. Ici, on évite simplement
 * d'afficher une page vide de 401 à quelqu'un dont la session est fermée.
 */
export const authGuard: CanActivateFn = (_route, etat) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.connecte()) {
    // Mot de passe initial non renouvelé : aucune vue métier n'est accessible
    // tant qu'il reste en place.
    if (auth.changementRequis()) {
      return router.createUrlTree(['/changer-mot-de-passe']);
    }
    return true;
  }

  // On mémorise la destination pour y ramener l'utilisateur après connexion.
  return router.createUrlTree(['/connexion'], {
    queryParams: { retour: etat.url },
  });
};
