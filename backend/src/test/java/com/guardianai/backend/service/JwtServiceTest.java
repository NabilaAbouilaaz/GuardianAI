package com.guardianai.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guardianai.backend.domain.AppUser;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Emission et verification des jetons (RF-07).
 *
 * Ces tests protegent le cœur de l'authentification : un jeton accepte a tort
 * ouvrirait l'acces a toute la plateforme, sans qu'aucune trace n'en signale la
 * cause.
 */
class JwtServiceTest {

    private static final String CLE = "cle-de-test-suffisamment-longue-pour-hmac-sha256";
    private static final String AUTRE_CLE = "une-toute-autre-cle-egalement-assez-longue-ici";

    private static AppUser utilisateur() {
        return new AppUser("analyste", "Analyste securite",
                "$2a$10$empreinte", AppUser.ROLE_ANALYSTE);
    }

    @Test
    @DisplayName("Un jeton emis se relit avec les memes informations")
    void emetEtRelitUnJeton() {
        JwtService service = new JwtService(CLE, 8, "dev");
        AppUser u = utilisateur();

        Claims contenu = service.lire(service.emettre(u));

        assertNotNull(contenu);
        assertEquals(u.getId().toString(), contenu.getSubject());
        assertEquals("analyste", contenu.get("username", String.class));
        assertEquals("Analyste securite", contenu.get("nom", String.class));
        assertEquals(AppUser.ROLE_ANALYSTE, contenu.get("role", String.class));
    }

    /**
     * C'est le test le plus important du fichier : un jeton signe avec une autre
     * cle doit etre rejete. Sans cette verification, n'importe qui pourrait
     * forger un jeton en se declarant administrateur.
     */
    @Test
    @DisplayName("Un jeton signe avec une autre cle est rejete")
    void rejetteUnJetonSigneAilleurs() {
        String jeton = new JwtService(AUTRE_CLE, 8, "dev").emettre(utilisateur());

        assertNull(new JwtService(CLE, 8, "dev").lire(jeton));
    }

    @Test
    @DisplayName("Un jeton falsifie est rejete")
    void rejetteUnJetonAltere() {
        JwtService service = new JwtService(CLE, 8, "dev");
        String jeton = service.emettre(utilisateur());

        // On modifie un caractere de la charge utile : la signature ne correspond
        // plus, meme si la structure du jeton reste valide.
        String altere = jeton.substring(0, 30) + "X" + jeton.substring(31);

        assertNull(service.lire(altere));
    }

    @Test
    @DisplayName("Un jeton expire est rejete")
    void rejetteUnJetonExpire() {
        // Validite nulle : le jeton naît deja perime.
        JwtService service = new JwtService(CLE, 0, "dev");

        assertNull(service.lire(service.emettre(utilisateur())));
    }

    @Test
    void rejetteUneChaineQuiNEstPasUnJeton() {
        JwtService service = new JwtService(CLE, 8, "dev");

        assertNull(service.lire("pas-du-tout-un-jeton"));
        assertNull(service.lire(""));
    }

    /**
     * HMAC-SHA256 exige au moins 256 bits de cle. Certaines bibliotheques
     * acceptent une cle plus courte en silence, ce qui affaiblirait la signature
     * sans que personne ne s'en apercoive : on prefere refuser de demarrer.
     */
    @Test
    @DisplayName("Une cle trop courte empeche le demarrage")
    void refuseUneCleTropCourte() {
        IllegalStateException erreur = assertThrows(IllegalStateException.class,
                () -> new JwtService("trop-courte", 8, "dev"));

        assertEquals(true, erreur.getMessage().contains("32"));
    }

    /**
     * La cle par defaut figure dans le depot : l'accepter en production
     * permettrait a quiconque a lu le code de forger un jeton d'administrateur.
     */
    @Test
    @DisplayName("La cle par defaut est refusee hors developpement")
    void refuseLaCleParDefautEnProduction() {
        String defaut = "cle-de-developpement-guardianai-a-remplacer-en-production";

        IllegalStateException erreur = assertThrows(IllegalStateException.class,
                () -> new JwtService(defaut, 8, "production"));
        assertEquals(true, erreur.getMessage().contains("JWT_SECRET"));

        // Tolere en developpement, avec un avertissement au demarrage.
        assertNotNull(new JwtService(defaut, 8, "dev"));
    }

    @Test
    void exposeLaDureeDeValiditeEnSecondes() {
        assertEquals(8 * 3600, new JwtService(CLE, 8, "dev").validiteSecondes());
    }
}
