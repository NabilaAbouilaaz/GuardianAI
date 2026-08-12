package com.guardianai.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Generation des mots de passe initiaux.
 *
 * Ces mots de passe sont communiques a un utilisateur qui devra les retaper, puis
 * les remplacer. Trois proprietes comptent : ils doivent etre imprevisibles, ne
 * pas contenir de caracteres visuellement ambigus, et satisfaire la politique —
 * un mot de passe genere que le serveur refuserait ensuite serait absurde.
 */
class UtilisateurServiceTest {

    private final PolitiqueMotDePasse politique = new PolitiqueMotDePasse();

    /**
     * La generation est privee : on y accede par reflexion plutot que d'elargir
     * sa visibilite pour un test. Elle n'a aucune raison d'etre appelee ailleurs.
     */
    private String genererUnMotDePasse() throws Exception {
        UtilisateurService service = new UtilisateurService(null, null);
        Method methode = UtilisateurService.class.getDeclaredMethod("genererMotDePasse");
        methode.setAccessible(true);
        return (String) methode.invoke(service);
    }

    @Test
    @DisplayName("Le mot de passe genere satisfait la politique")
    void respecteLaPolitique() throws Exception {
        for (int i = 0; i < 50; i++) {
            String motDePasse = genererUnMotDePasse();
            List<String> manquements = politique.verifier(motDePasse, "nouvel.utilisateur");
            assertTrue(manquements.isEmpty(),
                    "Mot de passe refuse par la politique : " + motDePasse + " " + manquements);
        }
    }

    /**
     * Les caracteres ambigus sont exclus : 0 et O, 1 et l et I. Ce mot de passe
     * est destine a etre lu puis retape, souvent depuis un message ou un papier.
     */
    @Test
    @DisplayName("Aucun caractere visuellement ambigu")
    void eviteLesCaracteresAmbigus() throws Exception {
        for (int i = 0; i < 50; i++) {
            String motDePasse = genererUnMotDePasse();
            for (char ambigu : new char[] {'0', 'O', '1', 'l', 'I'}) {
                assertEquals(-1, motDePasse.indexOf(ambigu),
                        "Caractere ambigu '" + ambigu + "' dans " + motDePasse);
            }
        }
    }

    @Test
    @DisplayName("Deux generations ne produisent pas le meme resultat")
    void produitDesValeursDistinctes() throws Exception {
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String motDePasse = genererUnMotDePasse();
            assertTrue(vus.add(motDePasse), "Collision : " + motDePasse);
        }
        assertNotEquals(1, vus.size());
    }

    @Test
    void faitSeizeCaracteres() throws Exception {
        assertEquals(16, genererUnMotDePasse().length());
    }
}
