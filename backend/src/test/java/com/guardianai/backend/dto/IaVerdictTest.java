package com.guardianai.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traduction du vocabulaire du moteur vers celui de l'interface.
 *
 * Le moteur Python rend ses verdicts en francais, le frontend attend des
 * constantes en anglais. Cette correspondance est le seul point de contact entre
 * les deux conventions : une erreur ici afficherait un verdict inverse sans
 * qu'aucune exception ne soit levee.
 */
class IaVerdictTest {

    private static IaVerdict verdict(String classification) {
        return new IaVerdict("f.exe", "a".repeat(64), 1024, classification,
                0.5, 66.38, "modele", false, 10.0, null, null, null, null);
    }

    @Test
    @DisplayName("malveillant devient MALICIOUS")
    void traduitMalveillant() {
        assertEquals("MALICIOUS", verdict("malveillant").toFrontendStatus());
    }

    @Test
    @DisplayName("suspect devient SUSPICIOUS")
    void traduitSuspect() {
        assertEquals("SUSPICIOUS", verdict("suspect").toFrontendStatus());
    }

    @Test
    @DisplayName("benin devient CLEAN")
    void traduitBenin() {
        assertEquals("CLEAN", verdict("benin").toFrontendStatus());
    }

    /**
     * Une valeur inattendue tombe sur CLEAN par defaut.
     *
     * Ce choix merite d'etre conscient : en cas de verdict inconnu, on affiche
     * « sain » plutot que « malveillant ». C'est discutable pour un outil de
     * securite, mais l'alternative — alerter sur une valeur qu'on ne comprend
     * pas — generait de fausses alertes sans fondement. Le cas ne peut survenir
     * que si le contrat du moteur change sans que le backend soit adapte.
     */
    @Test
    void uneValeurInconnueRetombeSurClean() {
        assertEquals("CLEAN", verdict("valeur-imprevue").toFrontendStatus());
    }

    @Test
    @DisplayName("Une explication vide n'est pas une explication")
    void detecteLAbsenceDExplication() {
        assertFalse(verdict("benin").aUneExplication());

        IaVerdict sansContribution = new IaVerdict("f.exe", "a".repeat(64), 1024,
                "benin", 0.5, 66.38, "modele", false, 10.0, List.of(), 0.5, -8.0, 0.04);
        assertFalse(sansContribution.aUneExplication());
    }

    @Test
    void detecteLaPresenceDUneExplication() {
        IaVerdict avecContribution = new IaVerdict("f.exe", "a".repeat(64), 1024,
                "benin", 0.5, 66.38, "modele", false, 10.0,
                List.of(new IaContribution("En-tetes PE", -0.83, "benin")),
                0.5424, -8.3122, 0.04);
        assertTrue(avecContribution.aUneExplication());
    }
}
