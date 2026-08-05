package com.guardianai.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Contribution d'un groupe de caracteristiques au verdict d'une analyse.
 *
 * Ces lignes sont ecrites au moment de l'analyse et jamais modifiees ensuite :
 * elles constituent la justification archivee d'une decision, au meme titre que
 * le verdict lui-meme (exigence RF-11).
 *
 * Le lien vers l'analyse est porte par un simple identifiant plutot que par une
 * association JPA : les contributions sont toujours chargees explicitement, et
 * une relation bidirectionnelle n'apporterait ici qu'un risque de chargement
 * involontaire.
 */
@Entity
@Table(name = "scan_contribution")
public class ScanContribution {

    @Id
    private UUID id;

    @Column(name = "scan_result_id", nullable = false)
    private UUID scanResultId;

    @Column(nullable = false, length = 64)
    private String groupe;

    /** Contribution en log-odds. Positive, elle pousse vers "malveillant". */
    @Column(nullable = false)
    private double valeur;

    @Column(nullable = false, length = 16)
    private String direction;

    /** Position dans le classement par poids decroissant. */
    @Column(nullable = false)
    private int rang;

    protected ScanContribution() {
        // requis par JPA
    }

    public ScanContribution(UUID scanResultId, String groupe, double valeur,
                            String direction, int rang) {
        this.id = UUID.randomUUID();
        this.scanResultId = scanResultId;
        this.groupe = groupe;
        this.valeur = valeur;
        this.direction = direction;
        this.rang = rang;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanResultId() {
        return scanResultId;
    }

    public String getGroupe() {
        return groupe;
    }

    public double getValeur() {
        return valeur;
    }

    public String getDirection() {
        return direction;
    }

    public int getRang() {
        return rang;
    }
}
