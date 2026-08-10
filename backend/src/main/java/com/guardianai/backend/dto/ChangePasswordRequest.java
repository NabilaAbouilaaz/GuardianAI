package com.guardianai.backend.dto;

/** Demande de changement de mot de passe pour l'utilisateur connecte. */
public record ChangePasswordRequest(String ancienMotDePasse, String nouveauMotDePasse) {
}
