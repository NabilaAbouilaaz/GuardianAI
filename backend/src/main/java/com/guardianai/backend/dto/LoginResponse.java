package com.guardianai.backend.dto;

/**
 * Reponse a une connexion reussie.
 *
 * Le nom et le role sont renvoyes en clair a cote du jeton pour que l'interface
 * puisse les afficher immediatement, sans decoder le jeton elle-meme. Ces valeurs
 * sont un confort d'affichage : les controles d'acces s'appuient uniquement sur
 * le contenu signe du jeton, verifie a chaque requete cote serveur.
 */
public record LoginResponse(
        String token,
        String username,
        String nom,
        String role,
        long expireDansSecondes,
        /**
         * Vrai tant que le mot de passe initial n'a pas ete renouvele. L'interface
         * doit alors imposer le changement avant de donner acces aux vues metier.
         */
        boolean doitChangerMotDePasse) {
}
