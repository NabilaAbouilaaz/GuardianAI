package com.guardianai.backend.service;

import com.guardianai.backend.domain.ScanContribution;
import com.guardianai.backend.domain.ScanResult;
import com.guardianai.backend.dto.AlertRecordDto;
import com.guardianai.backend.dto.ContributionDto;
import com.guardianai.backend.dto.FileTypeCountDto;
import com.guardianai.backend.dto.IaContribution;
import com.guardianai.backend.dto.IaVerdict;
import com.guardianai.backend.dto.ScanRecordDto;
import com.guardianai.backend.dto.ServiceStatusDto;
import com.guardianai.backend.dto.TrendPointDto;
import com.guardianai.backend.repository.ScanContributionRepository;
import com.guardianai.backend.repository.ScanResultRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestration d'une analyse : appel du moteur IA, enregistrement du resultat,
 * puis mise a disposition des donnees du tableau de bord.
 */
@Service
public class ScanService {

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter HEURE =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final IaEngineClient iaEngine;
    private final ScanResultRepository repository;
    private final ScanContributionRepository contributions;

    public ScanService(IaEngineClient iaEngine, ScanResultRepository repository,
                       ScanContributionRepository contributions) {
        this.iaEngine = iaEngine;
        this.repository = repository;
        this.contributions = contributions;
    }

    /**
     * Analyse un fichier, conserve le verdict et sa justification.
     *
     * L'explication est demandee des l'analyse : la base ne garde que l'empreinte
     * du fichier, jamais son contenu. Ne pas l'enregistrer maintenant reviendrait
     * a rendre la decision definitivement inexplicable (exigence RF-11).
     */
    @Transactional
    public ScanRecordDto analyzeAndStore(MultipartFile file) {
        IaVerdict verdict = iaEngine.analyze(file, true);

        ScanResult analyse = new ScanResult(
                verdict.filename(),
                verdict.sha256(),
                verdict.tailleOctets(),
                extensionOf(verdict.filename()),
                verdict.toFrontendStatus(),
                verdict.scoreMalveillance(),
                verdict.seuilApplique(),
                verdict.modelVersion(),
                verdict.dureeMs());

        if (verdict.aUneExplication()) {
            analyse.attacherExplication(verdict.valeurDeBase(), verdict.sommeContributions());
        }

        ScanResult saved = repository.save(analyse);

        // Une explication manquante n'invalide pas l'analyse : le verdict reste
        // exploitable, seule sa justification fait defaut. On ne bloque donc pas.
        if (verdict.aUneExplication()) {
            List<ScanContribution> lignes = new ArrayList<>();
            int rang = 0;
            for (IaContribution c : verdict.contributions()) {
                lignes.add(new ScanContribution(
                        saved.getId(), c.groupe(), c.valeur(), c.direction(), rang++));
            }
            this.contributions.saveAll(lignes);
        }

        return toDto(saved);
    }

    /** Justification archivee d'une analyse, dans l'ordre de poids decroissant. */
    @Transactional(readOnly = true)
    public List<ContributionDto> contributionsOf(UUID scanId) {
        return contributions.findByScanResultIdOrderByRangAsc(scanId).stream()
                .map(c -> new ContributionDto(c.getGroupe(), c.getValeur(), c.getDirection()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScanRecordDto> recentScans(int max) {
        return repository.findAllByOrderByAnalyzedAtDesc(Limit.of(max))
                .stream().map(ScanService::toDto).toList();
    }

    /** Repartition des verdicts sur les sept derniers jours. */
    @Transactional(readOnly = true)
    public List<TrendPointDto> weeklyTrend() {
        Instant depuis = Instant.now().minus(Duration.ofDays(7));
        List<ScanResult> analyses = repository.findByAnalyzedAtAfterOrderByAnalyzedAtDesc(depuis);

        // On prepare les sept jours dans l'ordre chronologique, meme vides :
        // un jour sans analyse doit apparaitre a zero, pas disparaitre du graphique.
        Map<LocalDate, long[]> parJour = new LinkedHashMap<>();
        LocalDate aujourdhui = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            parJour.put(aujourdhui.minusDays(i), new long[3]);
        }

        for (ScanResult a : analyses) {
            LocalDate jour = a.getAnalyzedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            long[] compteurs = parJour.get(jour);
            if (compteurs == null) {
                continue;
            }
            switch (a.getClassification()) {
                case "MALICIOUS" -> compteurs[0]++;
                case "SUSPICIOUS" -> compteurs[1]++;
                default -> compteurs[2]++;
            }
        }

        List<TrendPointDto> points = new ArrayList<>();
        parJour.forEach((jour, c) -> points.add(new TrendPointDto(
                jour.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                c[0], c[1], c[2])));
        return points;
    }

    @Transactional(readOnly = true)
    public List<FileTypeCountDto> fileTypeBreakdown() {
        Map<String, Long> compte = new LinkedHashMap<>();
        for (ScanResult a : repository.findAll()) {
            String type = a.getFileType() == null ? "autre" : a.getFileType();
            compte.merge(type, 1L, Long::sum);
        }
        return compte.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new FileTypeCountDto(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Alertes derivees des analyses malveillantes.
     *
     * Version provisoire : une detection malveillante genere une alerte. La gestion
     * complete du cycle de vie (assignation, cloture) reste a construire.
     */
    @Transactional(readOnly = true)
    public List<AlertRecordDto> alerts(int max) {
        return repository.findAllByOrderByAnalyzedAtDesc(Limit.of(200)).stream()
                .filter(a -> !"CLEAN".equals(a.getClassification()))
                .sorted(Comparator.comparing(ScanResult::getAnalyzedAt).reversed())
                .limit(max)
                .map(a -> new AlertRecordDto(
                        "ALT-" + a.getId().toString().substring(0, 4).toUpperCase(Locale.ROOT),
                        a.getId().toString(),
                        titreAlerte(a),
                        a.getFilename(),
                        "MALICIOUS".equals(a.getClassification()) ? "CRITICAL" : "MEDIUM",
                        HEURE.format(a.getAnalyzedAt()),
                        "Moteur IA",
                        "OPEN"))
                .toList();
    }

    /** Etat reel des composants de la plateforme. */
    @Transactional(readOnly = true)
    public List<ServiceStatusDto> serviceStatuses() {
        long debut = System.nanoTime();
        boolean moteurOk = iaEngine.isAvailable();
        long latenceMs = (System.nanoTime() - debut) / 1_000_000;

        long debutBase = System.nanoTime();
        boolean baseOk;
        try {
            repository.count();
            baseOk = true;
        } catch (Exception e) {
            baseOk = false;
        }
        long latenceBaseMs = (System.nanoTime() - debutBase) / 1_000_000;

        return List.of(
                new ServiceStatusDto("Backend Spring Boot", "OPERATIONAL", "-", "-"),
                new ServiceStatusDto("Moteur IA (FastAPI)",
                        moteurOk ? "OPERATIONAL" : "DOWN",
                        moteurOk ? latenceMs + "ms" : "-", "-"),
                new ServiceStatusDto("PostgreSQL",
                        baseOk ? "OPERATIONAL" : "DOWN",
                        baseOk ? latenceBaseMs + "ms" : "-", "-"));
    }

    // --- Conversions internes

    private static ScanRecordDto toDto(ScanResult a) {
        return new ScanRecordDto(
                "SCN-" + a.getId().toString().substring(0, 6).toUpperCase(Locale.ROOT),
                a.getId().toString(),
                a.getFilename(),
                a.getSha256() == null ? "" : a.getSha256().substring(0, Math.min(16, a.getSha256().length())),
                a.getClassification(),
                confiance(a),
                "Moteur IA",
                HORODATAGE.format(a.getAnalyzedAt()),
                tailleLisible(a.getSizeBytes()),
                a.getFileType() == null ? "-" : a.getFileType());
    }

    /**
     * Confiance affichee a l'utilisateur.
     *
     * Le modele renvoie une probabilite de malveillance. Pour un fichier juge sain,
     * la confiance pertinente est la probabilite complementaire : un score de
     * malveillance de 0,04 % correspond a une confiance de 99,96 % dans le verdict.
     */
    private static double confiance(ScanResult a) {
        double score = a.getScore();
        double valeur = "CLEAN".equals(a.getClassification()) ? 100.0 - score : score;
        return Math.round(valeur * 100.0) / 100.0;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "autre";
        }
        int point = filename.lastIndexOf('.');
        if (point < 0 || point == filename.length() - 1) {
            return "autre";
        }
        return "." + filename.substring(point + 1).toLowerCase(Locale.ROOT);
    }

    private static String tailleLisible(long octets) {
        if (octets < 1024) {
            return octets + " B";
        }
        if (octets < 1024 * 1024) {
            return Math.round(octets / 1024.0) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", octets / 1024.0 / 1024.0);
    }

    private static String titreAlerte(ScanResult a) {
        return "MALICIOUS".equals(a.getClassification())
                ? "Fichier malveillant detecte"
                : "Fichier suspect a verifier";
    }
}
