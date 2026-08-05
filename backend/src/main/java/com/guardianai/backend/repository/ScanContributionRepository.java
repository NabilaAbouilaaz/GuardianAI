package com.guardianai.backend.repository;

import com.guardianai.backend.domain.ScanContribution;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanContributionRepository extends JpaRepository<ScanContribution, UUID> {

    /** Contributions d'une analyse, dans l'ordre d'affichage retenu a l'ecriture. */
    List<ScanContribution> findByScanResultIdOrderByRangAsc(UUID scanResultId);
}
