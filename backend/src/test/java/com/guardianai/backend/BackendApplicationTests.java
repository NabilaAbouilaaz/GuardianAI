package com.guardianai.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifie que le contexte Spring se construit entierement.
 *
 * Ce test ne valide aucune regle metier, mais il attrape une categorie d'erreurs
 * que les tests unitaires ne voient pas : dependance manquante, bean impossible
 * a construire, migration Flyway invalide, propriete de configuration absente.
 *
 * Il exige une base PostgreSQL accessible, puisque Flyway s'execute au demarrage.
 * Les regles metier, elles, sont testees sans infrastructure dans les classes
 * ScanServiceCalculsTest, PolitiqueMotDePasseTest, JwtServiceTest et IaVerdictTest.
 */
@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
