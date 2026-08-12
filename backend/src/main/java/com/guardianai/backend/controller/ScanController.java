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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * Detaille ou non les erreurs inattendues renvoyees au client.
     *
     * Vrai par defaut pour ne pas gener le developpement ; a passer a faux en
     * production, ou le detail technique renseignerait un attaquant.
     */
    private final boolean detailsErreurs;

    public ScanController(ScanService scanService,
                          @Value("${guardianai.erreurs.detaillees:true}") boolean detailsErreurs) {
        this.scanService = scanService;
        this.detailsErreurs = detailsErreurs;
    }

    /**
     * Analyse un fichier envoye depuis l'interface.
     *
     * L'identite de l'appelant provient du jeton verifie en amont, jamais d'un
     * parametre de la requete : un client pourrait sinon attribuer son analyse a
     * n'importe qui, ce qui viderait la tracabilite de son sens (RF-11).
     */
    @PostMapping("/scan")
    public ScanRecordDto scan(@RequestParam("file") MultipartFile file,
                              Authentication authentification) {
        return scanService.analyzeAndStore(file, UUID.fromString(authentification.getName()));
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

    /**
     * Enregistre l'appreciation d'un analyste sur un verdict.
     *
     * Valeurs acceptees : CONFIRME, FAUX_POSITIF, TRAITE. L'auteur provient du
     * jeton, jamais du corps de la requete : un avis anonyme ou attribue a
     * autrui n'aurait aucune valeur probante.
     */
    @PostMapping("/scans/{id}/avis")
    public ResponseEntity<Map<String, String>> avis(@PathVariable UUID id,
                                                    @RequestBody Map<String, String> corps,
                                                    Authentication authentification) {
        scanService.enregistrerAvis(id,
                corps.get("avis"),
                corps.get("commentaire"),
                corps.get("criticite"),
                UUID.fromString(authentification.getName()));
        return ResponseEntity.ok(Map.of("message", "Avis enregistre."));
    }

    /** Un avis inconnu ou une analyse inexistante relevent de la requete, pas du serveur. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> demandeInvalide(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erreur", e.getMessage()));
    }

    @GetMapping("/alerts")
    public List<AlertRecordDto> alerts(@RequestParam(defaultValue = "20") int limit) {
        return scanService.alerts(limit);
    }

    /**
     * Etat detaille des composants. Reserve aux comptes authentifies.
     *
     * Cet endpoint revele la pile technique, les composants en service et leurs
     * temps de reponse — donc, par ricochet, les moments ou la plateforme est
     * fragile. C'est de la reconnaissance offerte a qui la demande.
     */
    @GetMapping("/status")
    public List<ServiceStatusDto> status() {
        return scanService.serviceStatuses();
    }

    /**
     * Signal de vie, accessible sans compte.
     *
     * Un endpoint public ne doit rien reveler d'autre que « le service repond ».
     * Il permet a une supervision externe de detecter une panne sans rien
     * apprendre de l'architecture.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
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
     * Filet de securite pour toute erreur non prevue.
     *
     * La trace complete part systematiquement dans les journaux du serveur. Ce
     * qui est renvoye au client, en revanche, depend de la configuration :
     *
     * - en developpement, le detail technique accelere le diagnostic ;
     * - en production, il renseignerait un attaquant sur les composants
     *   utilises, leurs versions et la structure interne de l'application.
     *
     * L'identifiant retourne dans les deux cas permet de relier une erreur vue
     * par l'utilisateur a la ligne correspondante dans les journaux, sans rien
     * divulguer.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> erreurInattendue(Exception e) {
        String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Erreur inattendue [{}] lors du traitement de la requete", reference, e);

        if (!detailsErreurs) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "erreur", "Une erreur interne est survenue.",
                    "reference", reference));
        }

        Throwable racine = e;
        while (racine.getCause() != null) {
            racine = racine.getCause();
        }

        return ResponseEntity.internalServerError().body(Map.of(
                "erreur", e.getClass().getSimpleName(),
                "message", String.valueOf(e.getMessage()),
                "cause", racine.getClass().getName() + " : " + racine.getMessage(),
                "reference", reference));
    }
}
