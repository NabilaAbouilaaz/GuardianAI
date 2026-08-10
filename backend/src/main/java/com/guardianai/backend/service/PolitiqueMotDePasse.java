package com.guardianai.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Regles de robustesse imposees aux mots de passe.
 *
 * La longueur prime sur la complexite : une phrase longue resiste mieux qu'une
 * suite courte de caracteres exotiques, et se retient sans etre notee sur un
 * papier. Les exigences de composition restent modestes pour cette raison.
 */
@Component
public class PolitiqueMotDePasse {

    private static final int LONGUEUR_MINIMALE = 12;

    /** Mots de passe trop evidents pour etre acceptes, meme longs. */
    private static final List<String> INTERDITS = List.of(
            "guardian", "motdepasse", "password", "azerty", "qwerty", "123456");

    /**
     * @return la liste des regles non respectees. Vide si le mot de passe convient.
     *
     * On retourne tous les manquements d'un coup plutot que le premier : corriger
     * un mot de passe en decouvrant les regles une par une est decourageant.
     */
    public List<String> verifier(String motDePasse, String username) {
        List<String> manquements = new ArrayList<>();

        if (motDePasse == null || motDePasse.length() < LONGUEUR_MINIMALE) {
            manquements.add("Au moins " + LONGUEUR_MINIMALE + " caracteres.");
        }
        if (motDePasse == null) {
            return manquements;
        }

        if (motDePasse.chars().noneMatch(Character::isLetter)) {
            manquements.add("Au moins une lettre.");
        }
        if (motDePasse.chars().noneMatch(Character::isDigit)) {
            manquements.add("Au moins un chiffre.");
        }

        String minuscule = motDePasse.toLowerCase(Locale.ROOT);

        if (username != null && minuscule.contains(username.toLowerCase(Locale.ROOT))) {
            manquements.add("Ne doit pas contenir votre identifiant.");
        }
        for (String interdit : INTERDITS) {
            if (minuscule.contains(interdit)) {
                manquements.add("Trop previsible : evitez « " + interdit + " ».");
                break;
            }
        }

        return manquements;
    }
}
