package com.guardianai.backend.dto;

/** Point de la courbe de tendance hebdomadaire du tableau de bord. */
public record TrendPointDto(String day, long malicious, long suspicious, long clean) {
}
