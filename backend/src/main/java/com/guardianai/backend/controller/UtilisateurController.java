package com.guardianai.backend.controller;

import com.guardianai.backend.dto.CreerUtilisateurRequest;
import com.guardianai.backend.dto.UtilisateurCreeResponse;
import com.guardianai.backend.dto.UtilisateurDto;
import com.guardianai.backend.service.UtilisateurService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration des comptes (RF-07).
 *
 * L'ensemble de ces operations est reserve au role ADMINISTRATEUR, la restriction
 * etant declaree dans SecurityConfig plutot que repetee sur chaque methode.
 */
@RestController
@RequestMapping("/api/v1/utilisateurs")
@Tag(name = "Utilisateurs", description = "Administration des comptes. Reserve aux administrateurs.")
public class UtilisateurController {

    private final UtilisateurService service;

    public UtilisateurController(UtilisateurService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste les comptes existants")
    public List<UtilisateurDto> lister() {
        return service.lister();
    }

    @PostMapping
    @Operation(summary = "Cree un compte et retourne son mot de passe initial",
            description = "Le mot de passe genere n'apparait que dans cette reponse : il n'est "
                    + "jamais stocke en clair et ne pourra pas etre reaffiche. Son "
                    + "renouvellement est impose a la premiere connexion.")
    public UtilisateurCreeResponse creer(@RequestBody CreerUtilisateurRequest demande) {
        return service.creer(demande);
    }

    @PostMapping("/{id}/activation")
    @Operation(summary = "Active ou desactive un compte",
            description = "Un compte n'est jamais supprime : les analyses conservent "
                    + "l'identifiant de leur auteur (RF-11).")
    public ResponseEntity<Map<String, String>> activation(@PathVariable UUID id,
                                                          @RequestBody Map<String, Boolean> corps,
                                                          Authentication authentification) {
        boolean actif = Boolean.TRUE.equals(corps.get("actif"));
        service.changerActivation(id, actif, UUID.fromString(authentification.getName()));
        return ResponseEntity.ok(Map.of(
                "message", actif ? "Compte active." : "Compte desactive."));
    }

    @PostMapping("/{id}/reinitialiser")
    @Operation(summary = "Reinitialise le mot de passe d'un compte",
            description = "Genere un nouveau mot de passe, leve tout blocage en cours et "
                    + "impose le renouvellement a la prochaine connexion.")
    public UtilisateurCreeResponse reinitialiser(@PathVariable UUID id) {
        return service.reinitialiserMotDePasse(id);
    }

    /** Identifiant deja pris, role inconnu, compte introuvable : la demande est en cause. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> demandeInvalide(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("erreur", e.getMessage()));
    }
}
