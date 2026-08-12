package com.guardianai.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regles de robustesse des mots de passe (RF-07). */
class PolitiqueMotDePasseTest {

    private final PolitiqueMotDePasse politique = new PolitiqueMotDePasse();

    @Test
    @DisplayName("Un mot de passe conforme ne souleve aucune objection")
    void accepteUnMotDePasseValide() {
        assertTrue(politique.verifier("Tempete-Bleue-42", "analyste").isEmpty());
    }

    @Test
    void refuseUnMotDePasseTropCourt() {
        List<String> manquements = politique.verifier("Court-1", "analyste");
        assertFalse(manquements.isEmpty());
        // get(0) et non getFirst() : cette derniere n'existe que depuis Java 21,
        // et le projet cible Java 17.
        assertTrue(manquements.get(0).contains("12"));
    }

    @Test
    void exigeAuMoinsUnChiffre() {
        assertTrue(politique.verifier("MotDePasseSansChiffre", "analyste")
                .stream().anyMatch(m -> m.contains("chiffre")));
    }

    @Test
    void exigeAuMoinsUneLettre() {
        assertTrue(politique.verifier("123456789012345", "analyste")
                .stream().anyMatch(m -> m.contains("lettre")));
    }

    /**
     * Un mot de passe contenant l'identifiant est trivial a deviner : c'est la
     * premiere chose qu'un attaquant essaie apres avoir obtenu un nom de compte.
     */
    @Test
    void refuseUnMotDePasseContenantLIdentifiant() {
        assertTrue(politique.verifier("analyste-2026-xyz", "analyste")
                .stream().anyMatch(m -> m.contains("identifiant")));
    }

    @Test
    @DisplayName("La comparaison a l'identifiant ignore la casse")
    void refuseAussiEnCasseDifferente() {
        assertFalse(politique.verifier("ANALYSTE-2026-xyz", "analyste").isEmpty());
    }

    /**
     * Les mots de passe initiaux contiennent « Guardian » et sont donc refuses.
     * C'est voulu : le renouvellement impose ne doit pas pouvoir se solder par
     * une simple variante du mot de passe communique.
     */
    @Test
    void refuseUneVarianteDuMotDePasseInitial() {
        assertFalse(politique.verifier("An4lyste-Guardian-2027", "analyste").isEmpty());
    }

    @Test
    void refuseLesTermesTropPrevisibles() {
        assertFalse(politique.verifier("azerty123456789", "admin").isEmpty());
        assertFalse(politique.verifier("password1234567", "admin").isEmpty());
    }

    /**
     * Tous les manquements sont retournes d'un coup : decouvrir les regles une
     * par une, a chaque tentative refusee, decouragerait l'utilisateur.
     */
    @Test
    void signaleTousLesManquementsEnUneFois() {
        List<String> manquements = politique.verifier("abc", "analyste");
        assertEquals(2, manquements.size(), "longueur et chiffre manquant");
    }

    @Test
    void supporteUnMotDePasseNul() {
        assertFalse(politique.verifier(null, "analyste").isEmpty());
    }
}
