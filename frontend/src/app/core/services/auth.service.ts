import { HttpClient } from '@angular/common/http';
import { Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface Utilisateur {
  username: string;
  nom: string;
  role: 'ANALYSTE' | 'ADMINISTRATEUR';
}

interface ReponseConnexion extends Utilisateur {
  token: string;
  expireDansSecondes: number;
  doitChangerMotDePasse: boolean;
}

/**
 * Session de l'utilisateur connecté (exigence RF-07).
 *
 * Le jeton est conservé dans `localStorage` : il survit ainsi à un rechargement
 * de page, ce qu'un analyste attend d'un outil de travail. Le compromis est
 * assumé — un stockage en mémoire seule résisterait mieux à une injection de
 * script, mais obligerait à se reconnecter à chaque F5.
 *
 * L'état est exposé en signaux : les composants qui affichent l'utilisateur se
 * mettent à jour sans abonnement à gérer.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly CLE_JETON = 'guardianai.token';
  private static readonly CLE_UTILISATEUR = 'guardianai.utilisateur';

  private readonly api = 'http://localhost:8080/api/v1/auth';

  private static readonly CLE_CHANGEMENT = 'guardianai.changementRequis';

  private readonly _utilisateur = signal<Utilisateur | null>(this.relire());
  private readonly _changementRequis = signal<boolean>(
    localStorage.getItem(AuthService.CLE_CHANGEMENT) === '1',
  );

  readonly utilisateur = this._utilisateur.asReadonly();
  readonly changementRequis = this._changementRequis.asReadonly();
  readonly connecte = computed(() => this._utilisateur() !== null);
  readonly estAdministrateur = computed(
    () => this._utilisateur()?.role === 'ADMINISTRATEUR',
  );

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  get jeton(): string | null {
    return localStorage.getItem(AuthService.CLE_JETON);
  }

  connexion(username: string, password: string): Observable<ReponseConnexion> {
    return this.http
      .post<ReponseConnexion>(`${this.api}/login`, { username, password })
      .pipe(
        tap((r) => {
          const utilisateur: Utilisateur = {
            username: r.username,
            nom: r.nom,
            role: r.role,
          };
          localStorage.setItem(AuthService.CLE_JETON, r.token);
          localStorage.setItem(
            AuthService.CLE_UTILISATEUR,
            JSON.stringify(utilisateur),
          );
          localStorage.setItem(
            AuthService.CLE_CHANGEMENT,
            r.doitChangerMotDePasse ? '1' : '0',
          );
          this._utilisateur.set(utilisateur);
          this._changementRequis.set(r.doitChangerMotDePasse);
        }),
      );
  }

  /**
   * Change le mot de passe et lève l'obligation qui bloquait l'accès.
   *
   * L'ancien mot de passe est exigé par le serveur même en session ouverte : une
   * session laissée sans surveillance ne doit pas suffire à s'approprier le compte.
   */
  changerMotDePasse(ancien: string, nouveau: string): Observable<unknown> {
    return this.http
      .post(`${this.api}/mot-de-passe`, {
        ancienMotDePasse: ancien,
        nouveauMotDePasse: nouveau,
      })
      .pipe(
        tap(() => {
          localStorage.setItem(AuthService.CLE_CHANGEMENT, '0');
          this._changementRequis.set(false);
        }),
      );
  }

  /**
   * @param motif message affiché sur l'écran de connexion. Distingue une
   *              déconnexion volontaire d'une session expirée — l'utilisateur
   *              doit savoir laquelle des deux vient de se produire.
   */
  deconnexion(motif?: string): void {
    // On prévient le serveur avant d'effacer le jeton : lui seul peut le rendre
    // caduc. L'effacer localement ne ferait qu'oublier un jeton qui resterait
    // valable jusqu'à son expiration.
    //
    // On n'attend pas la réponse pour fermer la session côté navigateur : si le
    // serveur est injoignable, l'utilisateur doit pouvoir se déconnecter quand
    // même. Le jeton expirera alors naturellement.
    if (this.jeton) {
      this.http.post(`${this.api}/logout`, {}).subscribe({
        next: () => undefined,
        error: () => undefined,
      });
    }

    localStorage.removeItem(AuthService.CLE_JETON);
    localStorage.removeItem(AuthService.CLE_UTILISATEUR);
    localStorage.removeItem(AuthService.CLE_CHANGEMENT);
    this._utilisateur.set(null);
    this._changementRequis.set(false);
    this.router.navigate(['/connexion'], {
      queryParams: motif ? { motif } : undefined,
    });
  }

  /**
   * Revalide la session auprès du serveur au démarrage de l'application.
   *
   * Sans cela, l'interface se fierait indéfiniment à ce qu'elle a mémorisé lors
   * de la connexion. Une session ouverte avant qu'un changement de mot de passe
   * ne devienne obligatoire contournerait donc l'obligation — le serveur, lui,
   * refuserait, mais l'utilisateur ne comprendrait pas pourquoi.
   *
   * C'est aussi ce qui détecte un jeton devenu invalide après un redémarrage du
   * backend avec une autre clé de signature.
   */
  revaliderSession(): void {
    if (!this.jeton) {
      return;
    }

    this.http.get<{ doitChangerMotDePasse: boolean }>(`${this.api}/moi`).subscribe({
      next: (r) => {
        localStorage.setItem(
          AuthService.CLE_CHANGEMENT,
          r.doitChangerMotDePasse ? '1' : '0',
        );
        this._changementRequis.set(r.doitChangerMotDePasse);
        if (r.doitChangerMotDePasse) {
          this.router.navigate(['/changer-mot-de-passe']);
        }
      },
      // Un 401 est déjà traité par l'intercepteur, qui ferme la session.
      error: () => undefined,
    });
  }

  private relire(): Utilisateur | null {
    const brut = localStorage.getItem(AuthService.CLE_UTILISATEUR);
    if (!brut || !localStorage.getItem(AuthService.CLE_JETON)) {
      return null;
    }
    try {
      return JSON.parse(brut) as Utilisateur;
    } catch {
      // Contenu corrompu : on repart d'une session vide plutôt que de laisser
      // l'application dans un état incohérent.
      return null;
    }
  }
}
