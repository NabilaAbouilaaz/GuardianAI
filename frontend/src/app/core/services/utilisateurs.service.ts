import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface Utilisateur {
  id: string;
  username: string;
  nom: string;
  role: 'ANALYSTE' | 'ADMINISTRATEUR';
  actif: boolean;
  doitChangerMotDePasse: boolean;
  derniereConnexion: string | null;
}

export interface CompteCree {
  username: string;
  nom: string;
  role: string;
  motDePasseInitial: string;
}

/**
 * Administration des comptes (RF-07).
 *
 * Toutes ces opérations sont réservées au rôle administrateur, la restriction
 * étant appliquée côté serveur. La garde de route côté client n'est qu'un
 * confort d'affichage.
 */
@Injectable({ providedIn: 'root' })
export class UtilisateursService {
  private readonly api = 'http://localhost:8080/api/v1/utilisateurs';

  constructor(private readonly http: HttpClient) {}

  lister(): Observable<Utilisateur[]> {
    return this.http.get<Utilisateur[]>(this.api);
  }

  /**
   * Crée un compte. Le mot de passe initial n'est renvoyé qu'ici, une seule
   * fois : il n'est jamais stocké en clair et ne pourra pas être réaffiché.
   */
  creer(username: string, nom: string, role: string): Observable<CompteCree> {
    return this.http.post<CompteCree>(this.api, { username, nom, role });
  }

  changerActivation(id: string, actif: boolean): Observable<unknown> {
    return this.http.post(`${this.api}/${id}/activation`, { actif });
  }

  reinitialiser(id: string): Observable<CompteCree> {
    return this.http.post<CompteCree>(`${this.api}/${id}/reinitialiser`, {});
  }
}
