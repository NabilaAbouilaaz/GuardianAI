package com.guardianai.backend.service;

import com.guardianai.backend.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emission et verification des jetons d'authentification (RF-07).
 *
 * Le jeton porte l'identifiant de l'utilisateur, son nom affichable et son role.
 * L'API etant sans session, c'est lui qui transporte l'identite a chaque appel :
 * le serveur ne conserve rien entre deux requetes.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /**
     * Valeur de repli inscrite dans application.yml.
     *
     * Elle permet de demarrer sans configuration en developpement. Mais elle est
     * publique — quiconque lit le depot peut forger un jeton d'administrateur.
     * Elle est donc refusee des que l'environnement n'est plus « dev ».
     */
    private static final String CLE_PAR_DEFAUT =
            "cle-de-developpement-guardianai-a-remplacer-en-production";

    private final SecretKey cle;
    private final Duration validite;

    public JwtService(@Value("${guardianai.jwt.secret}") String secret,
                      @Value("${guardianai.jwt.validite-heures:8}") long heures,
                      @Value("${guardianai.environnement:dev}") String environnement) {

        boolean developpement = "dev".equalsIgnoreCase(environnement);

        if (CLE_PAR_DEFAUT.equals(secret)) {
            if (!developpement) {
                throw new IllegalStateException(
                        "La cle de signature par defaut ne peut pas etre utilisee hors "
                                + "developpement : elle figure dans le depot, donc n'importe "
                                + "qui peut forger un jeton d'administrateur. Definir la "
                                + "variable d'environnement JWT_SECRET.");
            }
            log.warn("Cle de signature par defaut utilisee. Acceptable en developpement "
                    + "uniquement — definir JWT_SECRET avant tout deploiement.");
        }

        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);

        // HMAC-SHA256 exige au moins 256 bits de cle. Une cle plus courte serait
        // acceptee silencieusement par certaines bibliotheques, ce qui affaiblirait
        // la signature sans que personne ne s'en apercoive : on refuse de demarrer.
        if (octets.length < 32) {
            throw new IllegalStateException(
                    "guardianai.jwt.secret doit faire au moins 32 caracteres (256 bits). "
                            + "Longueur actuelle : " + octets.length);
        }

        this.cle = Keys.hmacShaKeyFor(octets);
        this.validite = Duration.ofHours(heures);
    }

    /** Emet un jeton pour un utilisateur authentifie. */
    public String emettre(AppUser utilisateur) {
        Instant maintenant = Instant.now();

        return Jwts.builder()
                .subject(utilisateur.getId().toString())
                .claim("username", utilisateur.getUsername())
                .claim("nom", utilisateur.getDisplayName())
                .claim("role", utilisateur.getRole())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(validite)))
                .signWith(cle)
                .compact();
    }

    /**
     * Verifie la signature et la date d'expiration, puis retourne le contenu.
     *
     * @return les informations portees par le jeton, ou null s'il est invalide,
     *         expire ou falsifie. Un jeton douteux ne doit jamais lever
     *         d'exception jusqu'au client : il est simplement ignore.
     */
    public Claims lire(String jeton) {
        try {
            return Jwts.parser()
                    .verifyWith(cle)
                    .build()
                    .parseSignedClaims(jeton)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long validiteSecondes() {
        return validite.toSeconds();
    }
}
