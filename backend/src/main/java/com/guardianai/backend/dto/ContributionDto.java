package com.guardianai.backend.dto;

/**
 * Contribution d'un groupe de caracteristiques, telle que consommee par le frontend.
 *
 * La valeur est en log-odds : son signe indique le sens, sa valeur absolue le poids
 * relatif. Elle ne s'interprete pas comme un pourcentage.
 */
public record ContributionDto(
        String groupe,
        double valeur,
        String direction) {
}
