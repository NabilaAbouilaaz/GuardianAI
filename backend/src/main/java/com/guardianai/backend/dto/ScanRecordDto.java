package com.guardianai.backend.dto;

/**
 * Analyse telle que consommee par le frontend Angular.
 * Les noms de champs correspondent a l'interface ScanRecord cote TypeScript.
 */
public record ScanRecordDto(
        String id,
        String filename,
        String hash,
        String status,
        double confidence,
        String analyst,
        String timestamp,
        String size,
        String type) {
}
