package com.guardianai.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Reponse brute du microservice d'analyse (FastAPI).
 *
 * Les noms de champs suivent ceux de l'API Python : on les mappe explicitement
 * plutot que de renommer cote Python, pour que le contrat reste lisible des deux cotes.
 *
 * Les quatre derniers champs ne sont renseignes que lorsque l'analyse a ete
 * demandee avec le parametre `expliquer`. Ils sont donc susceptibles d'etre nuls.
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
        @JsonProperty("duree_ms") Double dureeMs,
        List<IaContribution> contributions,
        @JsonProperty("valeur_de_base") Double valeurDeBase,
        @JsonProperty("somme_contributions") Double sommeContributions,
        @JsonProperty("score_reconstruit") Double scoreReconstruit) {

    /** Traduit le verdict francais du moteur vers le vocabulaire du frontend. */
    public String toFrontendStatus() {
        return switch (classification) {
            case "malveillant" -> "MALICIOUS";
            case "suspect" -> "SUSPICIOUS";
            default -> "CLEAN";
        };
    }

    /**
     * Vrai lorsque le moteur a fourni une explication exploitable.
     *
     * Une explication absente n'est pas une erreur : l'analyse reste valide et le
     * verdict doit etre conserve. Seule l'explicabilite est perdue.
     */
    public boolean aUneExplication() {
        return contributions != null && !contributions.isEmpty();
    }
}
