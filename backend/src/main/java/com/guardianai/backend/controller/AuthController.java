package com.guardianai.backend.controller;

import com.guardianai.backend.domain.AppUser;
import com.guardianai.backend.dto.ChangePasswordRequest;
import com.guardianai.backend.dto.LoginRequest;
import com.guardianai.backend.dto.LoginResponse;
import com.guardianai.backend.repository.AppUserRepository;
import com.guardianai.backend.service.JwtService;
import com.guardianai.backend.service.PolitiqueMotDePasse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connexion, identite et gestion du mot de passe (RF-07). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Nombre d'echecs consecutifs avant blocage temporaire. */
    private static final int SEUIL_ECHECS = 5;
    private static final Duration DUREE_BLOCAGE = Duration.ofMinutes(15);

    /**
     * Empreinte factice, verifiee lorsque le compte n'existe pas.
     *
     * BCrypt est lent par conception. Sans cette verification a vide, une
     * tentative sur un identifiant inconnu repondrait bien plus vite qu'une
     * tentative sur un compte existant, ce qui permettrait d'enumerer les
     * comptes en mesurant le temps de reponse.
     */
    private static final String EMPREINTE_FACTICE =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO1mB1Nl8Sd7Wl3sLcWlLbXpMhqQ0d1Zu";

    private final AppUserRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final JwtService jwtService;
    private final PolitiqueMotDePasse politique;

    public AuthController(AppUserRepository utilisateurs, PasswordEncoder encodeur,
                          JwtService jwtService, PolitiqueMotDePasse politique) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.jwtService = jwtService;
        this.politique = politique;
    }

    /**
     * Origine de la requete, telle qu'elle doit apparaitre dans les journaux.
     *
     * On lit d'abord X-Forwarded-For : derriere un proxy ou un repartiteur de
     * charge, l'adresse directe est celle du proxy, identique pour tout le monde,
     * donc inutile pour reperer une anomalie. L'en-tete est fourni par le client
     * et donc falsifiable — il sert a diagnostiquer, jamais a autoriser.
     */
    private static String origine(HttpServletRequest requete) {
        String transmise = requete.getHeader("X-Forwarded-For");
        String ip = transmise != null && !transmise.isBlank()
                ? transmise.split(",")[0].trim()
                : requete.getRemoteAddr();

        String agent = requete.getHeader("User-Agent");
        if (agent != null && agent.length() > 120) {
            agent = agent.substring(0, 120) + "…";
        }
        return ip + " | " + (agent == null ? "agent inconnu" : agent);
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> connexion(@RequestBody LoginRequest demande,
                                       HttpServletRequest requete) {
        Optional<AppUser> trouve = utilisateurs.findByUsernameIgnoreCase(demande.username());

        // Compte bloque : on repond avant meme de verifier le mot de passe, et on
        // indique le delai restant plutot qu'un refus opaque qui pousserait
        // l'utilisateur legitime a reessayer en boucle.
        if (trouve.isPresent() && trouve.get().estVerrouille()) {
            long minutes = Math.max(1, ChronoUnit.MINUTES.between(
                    java.time.Instant.now(), trouve.get().getLockedUntil()) + 1);
            log.warn("Tentative sur le compte verrouille '{}' depuis {}",
                    demande.username(), origine(requete));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "erreur", "Compte temporairement bloque apres plusieurs echecs. "
                            + "Reessayez dans " + minutes + " minute(s)."));
        }

        String empreinte = trouve.map(AppUser::getPasswordHash).orElse(EMPREINTE_FACTICE);
        boolean valide = encodeur.matches(demande.password(), empreinte);

        if (trouve.isEmpty() || !valide || !trouve.get().isEnabled()) {
            trouve.ifPresent(u -> {
                u.enregistrerEchec(SEUIL_ECHECS, DUREE_BLOCAGE);
                utilisateurs.save(u);
            });
            log.warn("Echec de connexion pour '{}' depuis {}",
                    demande.username(), origine(requete));
            // Message identique quel que soit le motif : distinguer "compte
            // inconnu" de "mot de passe errone" reviendrait a confirmer
            // l'existence d'un compte.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erreur", "Identifiant ou mot de passe incorrect."));
        }

        AppUser utilisateur = trouve.get();
        utilisateur.enregistrerConnexion();
        utilisateurs.save(utilisateur);
        log.info("Connexion de '{}' ({}) depuis {}",
                utilisateur.getUsername(), utilisateur.getRole(), origine(requete));

        return ResponseEntity.ok(new LoginResponse(
                jwtService.emettre(utilisateur),
                utilisateur.getUsername(),
                utilisateur.getDisplayName(),
                utilisateur.getRole(),
                jwtService.validiteSecondes(),
                utilisateur.doitChangerSonMotDePasse()));
    }

    /**
     * Changement du mot de passe de l'utilisateur connecte.
     *
     * L'ancien mot de passe est exige meme si la session est ouverte : sans cela,
     * un poste laisse sans surveillance quelques minutes suffirait a s'approprier
     * definitivement le compte.
     */
    @PostMapping("/mot-de-passe")
    @Transactional
    public ResponseEntity<?> changerMotDePasse(@RequestBody ChangePasswordRequest demande,
                                               Authentication authentification) {
        if (authentification == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<AppUser> trouve =
                utilisateurs.findById(UUID.fromString(authentification.getName()));
        if (trouve.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser utilisateur = trouve.get();

        if (!encodeur.matches(demande.ancienMotDePasse(), utilisateur.getPasswordHash())) {
            log.warn("Changement refuse pour '{}' : ancien mot de passe errone",
                    utilisateur.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erreur", "Mot de passe actuel incorrect."));
        }

        if (encodeur.matches(demande.nouveauMotDePasse(), utilisateur.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erreur", "Le nouveau mot de passe doit differer de l'ancien."));
        }

        List<String> manquements =
                politique.verifier(demande.nouveauMotDePasse(), utilisateur.getUsername());
        if (!manquements.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erreur", "Mot de passe trop faible.",
                    "regles", manquements));
        }

        utilisateur.changerMotDePasse(encodeur.encode(demande.nouveauMotDePasse()));
        utilisateurs.save(utilisateur);
        log.info("Mot de passe change pour '{}'", utilisateur.getUsername());

        return ResponseEntity.ok(Map.of("message", "Mot de passe mis a jour."));
    }

    /** Identite portee par le jeton courant. */
    @GetMapping("/moi")
    public ResponseEntity<?> moi(Authentication authentification) {
        if (authentification == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return utilisateurs.findById(UUID.fromString(authentification.getName()))
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(Map.of(
                        "username", u.getUsername(),
                        "nom", u.getDisplayName(),
                        "role", u.getRole(),
                        "doitChangerMotDePasse", u.doitChangerSonMotDePasse())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
