package com.guardianai.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reponse brute du microservice d'analyse (FastAPI).
 *
 * Les noms de champs suivent ceux de l'API Python : on les mappe explicitement
 * plutot que de renommer cote Python, pour que le contrat reste lisible des deux cotes.
 */
public record IaVerdict(
        String filename,
        String sha256,
        @JsonProperty("taille_octets") long tailleOctets,
        String classification,
        @JsonProperty("score_malveillance") double scoreMalveillance,
        @JsonProperty("seuil_applique") Double seuilApplique,
        @JsonProperty("model_version") String modelVersion,
        Boolean cache,
        @JsonProperty("duree_ms") Double dureeMs) {

    /** Traduit le verdict francais du moteur vers le vocabulaire du frontend. */
    public String toFrontendStatus() {
        return switch (classification) {
            case "malveillant" -> "MALICIOUS";
            case "suspect" -> "SUSPICIOUS";
            default -> "CLEAN";
        };
    }
}
