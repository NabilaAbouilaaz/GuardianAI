package com.guardianai.backend.dto;

/**
 * Reponse a une creation de compte.
 *
 * Le mot de passe initial n'apparait qu'ici, une seule fois : il n'est jamais
 * stocke en clair et ne peut donc pas etre reaffiche. L'administrateur doit le
 * transmettre immediatement, et il devient caduc des la premiere connexion.
 */
public record UtilisateurCreeResponse(
        String username,
        String nom,
        String role,
        String motDePasseInitial) {
}
