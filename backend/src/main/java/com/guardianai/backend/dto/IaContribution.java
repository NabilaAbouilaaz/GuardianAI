package com.guardianai.backend.dto;

/**
 * Contribution d'un groupe de caracteristiques au verdict, telle que renvoyee
 * par le moteur d'analyse.
 *
 * La valeur est exprimee en log-odds : c'est l'espace dans lequel LightGBM
 * additionne les sorties de ses arbres, et donc celui dans lequel les valeurs de
 * Shapley s'additionnent. Une valeur positive pousse vers "malveillant".
 */
public record IaContribution(
        String groupe,
        double valeur,
        String direction) {
}
