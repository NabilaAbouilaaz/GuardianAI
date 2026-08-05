package com.guardianai.backend.controller;

import com.guardianai.backend.dto.AlertRecordDto;
import com.guardianai.backend.dto.ContributionDto;
import com.guardianai.backend.dto.FileTypeCountDto;
import com.guardianai.backend.dto.ScanRecordDto;
import com.guardianai.backend.dto.ServiceStatusDto;
import com.guardianai.backend.dto.TrendPointDto;
import com.guardianai.backend.service.IaEngineClient;
import com.guardianai.backend.service.ScanService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Points d'entree consommes par le frontend Angular.
 *
 * Le prefixe /api/v1 laisse la possibilite de faire evoluer le contrat
 * sans casser les clients existants (exigence OBJ-06).
 */
@RestController
@RequestMapping("/api/v1")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** Analyse un fichier envoye depuis l'interface. */
    @PostMapping("/scan")
    public ScanRecordDto scan(@RequestParam("file") MultipartFile file) {
        return scanService.analyzeAndStore(file);
    }

    @GetMapping("/scans/recent")
    public List<ScanRecordDto> recent(@RequestParam(defaultValue = "20") int limit) {
        return scanService.recentScans(limit);
    }

    @GetMapping("/stats/trend")
    public List<TrendPointDto> trend() {
        return scanService.weeklyTrend();
    }

    @GetMapping("/stats/file-types")
    public List<FileTypeCountDto> fileTypes() {
        return scanService.fileTypeBreakdown();
    }

    /**
     * Justification archivee d'une analyse : contributions de chaque groupe de
     * caracteristiques au verdict, de la plus determinante a la moins.
     */
    @GetMapping("/scans/{id}/contributions")
    public List<ContributionDto> contributions(@PathVariable UUID id) {
        return scanService.contributionsOf(id);
    }

    @GetMapping("/alerts")
    public List<AlertRecordDto> alerts(@RequestParam(defaultValue = "20") int limit) {
        return scanService.alerts(limit);
    }

    @GetMapping("/status")
    public List<ServiceStatusDto> status() {
        return scanService.serviceStatuses();
    }

    // --- Traduction des erreurs metier en reponses HTTP explicites

    @ExceptionHandler(IaEngineClient.FileNotAnalyzableException.class)
    public ResponseEntity<Map<String, String>> fichierIllisible(
            IaEngineClient.FileNotAnalyzableException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("erreur", e.getMessage()));
    }

    @ExceptionHandler(IaEngineClient.IaEngineUnavailableException.class)
    public ResponseEntity<Map<String, String>> moteurIndisponible(
            IaEngineClient.IaEngineUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erreur", e.getMessage()));
    }

    /**
     * Filet de securite : toute erreur inattendue est journalisee et renvoyee
     * en clair plutot que sous la forme d'un 500 muet, pour rester diagnosticable.
     * A retirer ou a rendre generique avant une mise en production.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> erreurInattendue(Exception e) {
        log.error("Erreur inattendue lors du traitement de la requete", e);

        Throwable racine = e;
        while (racine.getCause() != null) {
            racine = racine.getCause();
        }

        return ResponseEntity.internalServerError().body(Map.of(
                "erreur", e.getClass().getSimpleName(),
                "message", String.valueOf(e.getMessage()),
                "cause", racine.getClass().getName() + " : " + racine.getMessage()));
    }
}
