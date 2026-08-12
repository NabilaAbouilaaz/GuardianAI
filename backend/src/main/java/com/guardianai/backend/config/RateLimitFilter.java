package com.guardianai.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite le nombre de requetes par origine sur les routes couteuses.
 *
 * Deux routes justifient une limite. `/auth/login` parce que le blocage de compte
 * ne protege pas contre l'essai d'un meme mot de passe sur des centaines de
 * comptes — chaque compte reste sous son seuil, mais l'attaque progresse.
 * `/scan` parce qu'une analyse mobilise jusqu'a quarante fois la taille du
 * fichier en memoire : quelques envois simultanes suffisent a mettre la machine a
 * genoux, sans qu'aucune faille ne soit exploitee.
 *
 * <p><b>Limite connue.</b> Le compteur vit en memoire : il repart de zero au
 * redemarrage et n'est pas partage entre plusieurs instances. Pour un deploiement
 * reparti, il faudrait le porter dans un magasin commun, ou confier la tache a un
 * repartiteur de charge. C'est suffisant ici, et le dire vaut mieux que de laisser
 * croire a une protection absolue.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Route surveillee, avec son quota et sa fenetre. */
    private record Quota(int maximum, Duration fenetre) {}

    private static final Map<String, Quota> QUOTAS = Map.of(
            // Une connexion legitime demande quelques essais, pas vingt.
            "/api/v1/auth/login", new Quota(10, Duration.ofMinutes(5)),
            // Une analyse dure environ une seconde : trente par minute represente
            // deja un usage soutenu.
            "/api/v1/scan", new Quota(30, Duration.ofMinutes(1)));

    /** Compteurs par origine et par route, remis a zero a chaque fenetre. */
    private final Map<String, Compteur> compteurs = new ConcurrentHashMap<>();

    private static final class Compteur {
        final AtomicInteger nombre = new AtomicInteger();
        volatile Instant debut = Instant.now();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete,
                                    HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {

        Quota quota = QUOTAS.get(requete.getRequestURI());
        if (quota == null) {
            chaine.doFilter(requete, reponse);
            return;
        }

        String cle = origine(requete) + "|" + requete.getRequestURI();
        Compteur compteur = compteurs.computeIfAbsent(cle, k -> new Compteur());

        synchronized (compteur) {
            if (Duration.between(compteur.debut, Instant.now()).compareTo(quota.fenetre()) > 0) {
                compteur.debut = Instant.now();
                compteur.nombre.set(0);
            }
        }

        if (compteur.nombre.incrementAndGet() > quota.maximum()) {
            long attente = quota.fenetre().minus(
                    Duration.between(compteur.debut, Instant.now())).toSeconds();

            log.warn("Quota depasse sur {} depuis {}", requete.getRequestURI(), origine(requete));

            // 429 n'a pas de constante dans l'API servlet : ses codes datent d'avant
            // l'ajout de ce statut. On passe par celle de Spring.
            reponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            reponse.setHeader("Retry-After", String.valueOf(Math.max(1, attente)));
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write(
                    "{\"erreur\":\"Trop de requetes. Reessayez dans "
                            + Math.max(1, attente) + " seconde(s).\"}");
            return;
        }

        // Purge grossiere : sans elle, la table grossirait indefiniment sur une
        // instance de longue duree. Declenchee rarement, elle ne coute rien.
        if (compteurs.size() > 10_000) {
            compteurs.entrySet().removeIf(e ->
                    Duration.between(e.getValue().debut, Instant.now()).toHours() >= 1);
        }

        chaine.doFilter(requete, reponse);
    }

    /**
     * X-Forwarded-For d'abord : derriere un proxy, l'adresse directe est la meme
     * pour tout le monde, et un quota par proxy bloquerait tous les utilisateurs
     * legitimes des le premier abus. L'en-tete etant falsifiable, cette limite
     * gene un usage abusif ordinaire mais n'arrete pas un attaquant determine.
     */
    private static String origine(HttpServletRequest requete) {
        String transmise = requete.getHeader("X-Forwarded-For");
        return transmise != null && !transmise.isBlank()
                ? transmise.split(",")[0].trim()
                : requete.getRemoteAddr();
    }
}
