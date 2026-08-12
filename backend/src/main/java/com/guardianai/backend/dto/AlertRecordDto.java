package com.guardianai.backend.dto;

/**
 * Alerte affichee a l'analyste, derivee d'une analyse jugee malveillante.
 *
 * `id` est un identifiant court, lisible et destine a l'affichage ; il est tronque
 * et ne permet pas de retrouver l'analyse. `scanId` porte l'identifiant reel, dont
 * le frontend a besoin pour demander la justification du verdict.
 */
public record AlertRecordDto(
        String id,
        String scanId,
        String title,
        String file,
        String severity,
        String time,
        String analyst,
        String status,
        /** Justification rédigée par l'analyste, nulle tant qu'aucun avis n'a été porté. */
        String commentaire,
        /** Vrai lorsque la criticité affichée a été fixée par l'analyste, pas déduite du verdict. */
        boolean criticiteAjustee) {
}
