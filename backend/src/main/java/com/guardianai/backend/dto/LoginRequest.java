package com.guardianai.backend.dto;

/** Identifiants soumis lors d'une tentative de connexion. */
public record LoginRequest(String username, String password) {
}
