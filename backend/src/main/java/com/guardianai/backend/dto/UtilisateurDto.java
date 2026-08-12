package com.guardianai.backend.dto;

/**
 * Compte utilisateur tel que presente a l'administrateur.
 *
 * L'empreinte du mot de passe n'y figure pas : elle n'a aucune raison de quitter
 * le serveur, meme vers un administrateur.
 */
public record UtilisateurDto(
        String id,
        String username,
        String nom,
        String role,
        boolean actif,
        boolean doitChangerMotDePasse,
        String derniereConnexion) {
}
