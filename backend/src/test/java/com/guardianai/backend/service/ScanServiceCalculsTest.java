package com.guardianai.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guardianai.backend.domain.ScanResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regles de calcul appliquees aux analyses.
 *
 * Ces methodes sont testees isolement, sans base ni contexte Spring : ce sont
 * des fonctions pures, et leur exactitude ne depend d'aucune infrastructure.
 */
class ScanServiceCalculsTest {

    /** Construit une analyse minimale, seuls le verdict et le score important ici. */
    private static ScanResult analyse(String classification, double score) {
        return new ScanResult("fichier.exe", "a".repeat(64), 1024, ".exe",
                classification, score, 66.38, "modele-test", 12.0);
    }

    @Nested
    @DisplayName("Confiance affichee a l'analyste")
    class Confiance {

        /**
         * Le modele renvoie une probabilite de malveillance. Pour un fichier juge
         * sain, la confiance pertinente est la probabilite complementaire :
         * annoncer « 0,04 % de confiance » sur un fichier declare benin serait
         * incomprehensible.
         */
        @Test
        void surUnFichierSainCEstLeComplementDuScore() {
            assertEquals(99.96, ScanService.confiance(analyse("CLEAN", 0.04)));
        }

        /** Sur un verdict malveillant, le score lui-meme fait office de confiance. */
        @Test
        void surUnFichierMalveillantCEstLeScore() {
            assertEquals(97.4, ScanService.confiance(analyse("MALICIOUS", 97.4)));
        }

        @Test
        void surUnFichierSuspectCEstAussiLeScore() {
            assertEquals(72.1, ScanService.confiance(analyse("SUSPICIOUS", 72.1)));
        }

        /** Deux decimales : au-dela, la precision affichee serait trompeuse. */
        @Test
        void leResultatEstArrondiADeuxDecimales() {
            assertEquals(66.67, ScanService.confiance(analyse("MALICIOUS", 66.666666)));
        }

        /** Cas limites : un score de 0 ou 100 ne doit pas produire d'aberration. */
        @Test
        void gereLesBornes() {
            assertEquals(100.0, ScanService.confiance(analyse("CLEAN", 0.0)));
            assertEquals(100.0, ScanService.confiance(analyse("MALICIOUS", 100.0)));
        }
    }

    @Nested
    @DisplayName("Statut d'une alerte selon l'avis de l'analyste")
    class StatutAlerte {

        @Test
        void resteOuverteTantQuePersonneNeSEstPrononce() {
            assertEquals("OPEN", ScanService.statutAlerte(analyse("MALICIOUS", 97.4)));
        }

        /**
         * La criticite retenue par l'analyste prime sur celle deduite du verdict :
         * le moteur ignore les actifs touches et l'exposition reelle.
         */
        @Test
        void laCriticiteDeLAnalystePrimeSurCelleDuVerdict() {
            ScanResult a = analyse("MALICIOUS", 97.4);
            assertEquals("CRITICAL", ScanService.criticite(a));

            a.recueillirAvis("CONFIRME", "Poste isole, hors production.",
                    "MEDIUM", java.util.UUID.randomUUID());
            assertEquals("MEDIUM", ScanService.criticite(a));
        }

        @Test
        void sansAvisLaCriticiteDecouleDuVerdict() {
            assertEquals("CRITICAL", ScanService.criticite(analyse("MALICIOUS", 97.4)));
            assertEquals("MEDIUM", ScanService.criticite(analyse("SUSPICIOUS", 72.1)));
        }

        @Test
        void passeEnCoursQuandLeVerdictEstConfirme() {
            ScanResult a = analyse("MALICIOUS", 97.4);
            a.recueillirAvis("CONFIRME", null, null, java.util.UUID.randomUUID());
            assertEquals("INVESTIGATING", ScanService.statutAlerte(a));
        }

        @Test
        void estCloturreeParUnFauxPositif() {
            ScanResult a = analyse("MALICIOUS", 97.4);
            a.recueillirAvis("FAUX_POSITIF", "Outil interne signe.", null,
                    java.util.UUID.randomUUID());
            assertEquals("RESOLVED", ScanService.statutAlerte(a));
        }

        @Test
        void estCloturreeParUnTraitement() {
            ScanResult a = analyse("SUSPICIOUS", 72.1);
            a.recueillirAvis("TRAITE", null, null, java.util.UUID.randomUUID());
            assertEquals("RESOLVED", ScanService.statutAlerte(a));
        }
    }

    @Nested
    @DisplayName("Extraction de l'extension")
    class Extension {

        @Test
        void extraitLExtensionEnMinuscules() {
            assertEquals(".exe", ScanService.extensionOf("notepad.EXE"));
            assertEquals(".dll", ScanService.extensionOf("kernel32.dll"));
        }

        /** Un nom compose ne doit pas induire en erreur : seul le dernier point compte. */
        @Test
        void retientLeDernierPoint() {
            assertEquals(".gz", ScanService.extensionOf("archive.tar.gz"));
        }

        @Test
        void retourneAutreQuandIlNYAPasDExtension() {
            assertEquals("autre", ScanService.extensionOf("Makefile"));
            assertEquals("autre", ScanService.extensionOf(null));
        }

        /**
         * Un nom se terminant par un point n'a pas d'extension exploitable.
         * Sans ce cas, le code produirait une chaine reduite au seul point.
         */
        @Test
        void retourneAutreQuandLeNomFinitParUnPoint() {
            assertEquals("autre", ScanService.extensionOf("fichier."));
        }
    }

    @Nested
    @DisplayName("Taille lisible")
    class Taille {

        @Test
        void afficheLesOctetsEnDessousDUnKilo() {
            assertEquals("512 B", ScanService.tailleLisible(512));
        }

        @Test
        void afficheLesKilooctetsEnDessousDUnMega() {
            assertEquals("352 KB", ScanService.tailleLisible(360448));
        }

        @Test
        void afficheLesMegaoctetsAuDela() {
            assertEquals("2.5 MB", ScanService.tailleLisible(2_621_440));
        }

        /**
         * Les bornes exactes sont verifiees : c'est la que les erreurs
         * d'inegalite stricte se logent habituellement.
         */
        @Test
        void gereLesBornesExactes() {
            assertEquals("1023 B", ScanService.tailleLisible(1023));
            assertEquals("1 KB", ScanService.tailleLisible(1024));
            assertEquals("1.0 MB", ScanService.tailleLisible(1024 * 1024));
        }
    }
}
