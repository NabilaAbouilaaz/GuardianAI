import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './services/auth.service';

/**
 * Joint le jeton à chaque appel de l'API et traite les refus d'accès.
 *
 * Centraliser cela ici évite que chaque service ait à y penser : un oubli
 * ailleurs se traduirait par un 401 incompréhensible sur une seule page.
 */
export const authInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const auth = inject(AuthService);
  const jeton = auth.jeton;

  // La connexion elle-même ne doit pas porter de jeton : en présenter un
  // expiré ferait échouer la tentative de reconnexion.
  const versConnexion = requete.url.includes('/auth/login');

  const requeteFinale =
    jeton && !versConnexion
      ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } })
      : requete;

  return suivant(requeteFinale).pipe(
    catchError((erreur: HttpErrorResponse) => {
      if (erreur.status === 401 && !versConnexion) {
        // Jeton absent, expiré ou invalide. Huit heures se sont écoulées, ou le
        // serveur a redémarré avec une autre clé de signature.
        auth.deconnexion('Votre session a expiré. Merci de vous reconnecter.');
      }
      return throwError(() => erreur);
    }),
  );
};
