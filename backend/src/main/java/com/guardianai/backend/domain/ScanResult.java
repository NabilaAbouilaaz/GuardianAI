package com.guardianai.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultat d'une analyse de fichier, tel que conserve en base.
 *
 * Cette table constitue la trace d'audit des analyses : on y garde l'empreinte
 * du fichier, le verdict rendu et la version du modele qui l'a rendu, afin de
 * pouvoir justifier a posteriori une decision.
 */
@Entity
@Table(name = "scan_result")
public class ScanResult {

    @Id
    private UUID id;

    @Column(nullable = false, length = 512)
    private String filename;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "file_type", length = 32)
    private String fileType;

    /** CLEAN, SUSPICIOUS ou MALICIOUS. */
    @Column(nullable = false, length = 16)
    private String classification;

    /** Probabilite de malveillance rendue par le modele, entre 0 et 100. */
    @Column(nullable = false)
    private double score;

    /** Seuil applique par le modele au moment de l'analyse. */
    private Double threshold;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "duration_ms")
    private Double durationMs;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    /**
     * Valeur de base du modele et somme des contributions SHAP, en log-odds.
     *
     * Conservees pour pouvoir revalider une explication archivee : la sigmoide de
     * leur somme doit redonner le score enregistre. Nulles lorsque l'analyse a ete
     * realisee sans explication.
     */
    @Column(name = "shap_base_value")
    private Double shapBaseValue;

    @Column(name = "shap_sum")
    private Double shapSum;

    /**
     * Auteur de l'analyse. Nul pour les analyses anterieures a la mise en place
     * de l'authentification : leur inventer un auteur fausserait la tracabilite.
     */
    @Column(name = "analyst_id")
    private UUID analystId;

    /**
     * Avis porte par un analyste sur le verdict du moteur.
     *
     * Nul tant que personne ne s'est prononce. C'est la seule facon de mesurer
     * le taux de faux positifs reel en exploitation, distinct de celui mesure
     * sur le jeu de test.
     */
    @Column(name = "analyst_feedback", length = 16)
    private String analystFeedback;

    @Column(name = "feedback_by")
    private UUID feedbackBy;

    @Column(name = "feedback_at")
    private Instant feedbackAt;

    /** Justification de l'avis, rédigée par l'analyste. */
    @Column(name = "analyst_comment", columnDefinition = "text")
    private String analystComment;

    /**
     * Criticite retenue par l'analyste, lorsqu'elle differe de celle deduite du
     * verdict. Le moteur ignore les actifs touches et l'exposition reelle.
     */
    @Column(name = "analyst_severity", length = 16)
    private String analystSeverity;

    protected ScanResult() {
        // requis par JPA
    }

    public ScanResult(String filename, String sha256, long sizeBytes, String fileType,
                      String classification, double score, Double threshold,
                      String modelVersion, Double durationMs) {
        this.id = UUID.randomUUID();
        this.filename = filename;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.fileType = fileType;
        this.classification = classification;
        this.score = score;
        this.threshold = threshold;
        this.modelVersion = modelVersion;
        this.durationMs = durationMs;
        this.analyzedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getFileType() {
        return fileType;
    }

    public String getClassification() {
        return classification;
    }

    public double getScore() {
        return score;
    }

    public Double getThreshold() {
        return threshold;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Double getDurationMs() {
        return durationMs;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Double getShapBaseValue() {
        return shapBaseValue;
    }

    public Double getShapSum() {
        return shapSum;
    }

    /**
     * Attache les references permettant de revalider l'explication.
     *
     * Methode distincte du constructeur : une analyse reste valide sans
     * explication, ces valeurs sont donc optionnelles par nature.
     */
    public void attacherExplication(Double baseValue, Double sum) {
        this.shapBaseValue = baseValue;
        this.shapSum = sum;
    }

    public UUID getAnalystId() {
        return analystId;
    }

    public void attribuerA(UUID analystId) {
        this.analystId = analystId;
    }

    public String getAnalystFeedback() {
        return analystFeedback;
    }

    public UUID getFeedbackBy() {
        return feedbackBy;
    }

    public Instant getFeedbackAt() {
        return feedbackAt;
    }

    /**
     * Enregistre l'avis d'un analyste.
     *
     * L'avis est modifiable : un analyste peut revenir sur son jugement apres
     * verification. Le verdict du moteur, lui, reste intact — on superpose une
     * appreciation humaine, on ne reecrit pas la mesure.
     */
    public void recueillirAvis(String avis, String commentaire, String criticite, UUID auteur) {
        this.analystFeedback = avis;
        this.analystComment = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
        this.analystSeverity = criticite;
        this.feedbackBy = auteur;
        this.feedbackAt = Instant.now();
    }

    public String getAnalystComment() {
        return analystComment;
    }

    public String getAnalystSeverity() {
        return analystSeverity;
    }
}
