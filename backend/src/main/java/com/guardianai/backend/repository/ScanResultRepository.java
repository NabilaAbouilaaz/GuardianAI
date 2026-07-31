package com.guardianai.backend.repository;

import com.guardianai.backend.domain.ScanResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanResultRepository extends JpaRepository<ScanResult, UUID> {

    /** Historique recent, du plus recent au plus ancien. */
    List<ScanResult> findAllByOrderByAnalyzedAtDesc(Limit limit);

    /** Analyses realisees depuis une date donnee (utilise pour les statistiques). */
    List<ScanResult> findByAnalyzedAtAfterOrderByAnalyzedAtDesc(Instant since);

    /** Recherche par empreinte (exigence RF-12). */
    List<ScanResult> findBySha256OrderByAnalyzedAtDesc(String sha256);
}
