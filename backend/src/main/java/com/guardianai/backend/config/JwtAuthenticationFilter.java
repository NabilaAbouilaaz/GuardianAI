package com.guardianai.backend.config;

import com.guardianai.backend.domain.AppUser;
import com.guardianai.backend.repository.AppUserRepository;
import com.guardianai.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reconnait l'utilisateur a partir du jeton porte par la requete.
 *
 * S'execute une fois par requete, avant les regles d'autorisation. En l'absence
 * de jeton valide, il ne bloque rien : il laisse simplement le contexte de
 * securite vide, et c'est la configuration qui decidera si la ressource demandee
 * exigeait une authentification. Cela permet aux routes ouvertes — la connexion,
 * notamment — de fonctionner sans traitement particulier ici.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;
    private final AppUserRepository utilisateurs;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository utilisateurs) {
        this.jwtService = jwtService;
        this.utilisateurs = utilisateurs;
    }

    /**
     * Confronte le jeton a l'etat actuel du compte.
     *
     * Une signature valide ne suffit pas : entre l'emission du jeton et son usage,
     * le compte a pu etre desactive, son mot de passe change, ou une deconnexion
     * demandee. Sans cette verification, aucune de ces actions ne prendrait effet
     * avant l'expiration naturelle du jeton — jusqu'a huit heures.
     *
     * <p>Cout : une lecture par cle primaire a chaque requete. C'est le prix d'une
     * revocation immediate, et il est modeste au regard d'une analyse de fichier
     * qui dure pres d'une seconde.
     */
    private boolean jetonEncoreValable(Claims contenu) {
        AppUser utilisateur;
        try {
            utilisateur = utilisateurs.findById(UUID.fromString(contenu.getSubject()))
                    .orElse(null);
        } catch (IllegalArgumentException identifiantMalforme) {
            return false;
        }

        if (utilisateur == null || !utilisateur.isEnabled()) {
            return false;
        }

        Instant limite = utilisateur.getTokensValidAfter();
        if (limite == null) {
            return true;
        }

        Date emisLe = contenu.getIssuedAt();
        return emisLe != null && emisLe.toInstant().isAfter(limite);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete,
                                    HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {

        String entete = requete.getHeader(HttpHeaders.AUTHORIZATION);

        if (entete != null && entete.startsWith(PREFIXE)) {
            Claims contenu = jwtService.lire(entete.substring(PREFIXE.length()));

            if (contenu != null && jetonEncoreValable(contenu)) {
                String role = contenu.get("role", String.class);

                // Spring Security attend le prefixe ROLE_ pour ses controles par
                // role. Il n'est pas stocke en base : le prefixe est une convention
                // du framework, pas une donnee metier.
                var authentification = new UsernamePasswordAuthenticationToken(
                        contenu.getSubject(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                authentification.setDetails(contenu.get("nom", String.class));
                SecurityContextHolder.getContext().setAuthentication(authentification);
            }
        }

        chaine.doFilter(requete, reponse);
    }
}
