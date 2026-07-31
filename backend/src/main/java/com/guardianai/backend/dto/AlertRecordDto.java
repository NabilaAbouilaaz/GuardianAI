package com.guardianai.backend.dto;

/** Alerte affichee a l'analyste, derivee d'une analyse jugee malveillante. */
public record AlertRecordDto(
        String id,
        String title,
        String file,
        String severity,
        String time,
        String analyst,
        String status) {
}
