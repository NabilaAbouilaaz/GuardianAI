package com.guardianai.backend.dto;

/** Demande de creation d'un compte. Le mot de passe est genere par le serveur. */
public record CreerUtilisateurRequest(String username, String nom, String role) {
}
