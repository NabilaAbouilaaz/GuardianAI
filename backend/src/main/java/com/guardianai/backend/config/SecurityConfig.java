package com.guardianai.backend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Securite applicative (exigence RF-07).
 *
 * L'API est desormais fermee par defaut : toute route non declaree ouverte exige
 * un jeton valide. Deux roles sont distingues, ANALYSTE et ADMINISTRATEUR.
 *
 * Le MFA prevu au cahier des charges n'est pas implemente ; il est documente
 * comme perspective plutot que livre a moitie.
 */
@Configuration
public class SecurityConfig {

    private final String allowedOrigins;

    /**
     * Ouvre ou non la documentation d'API sans authentification.
     *
     * Vraie en developpement, ou elle sert a explorer les endpoints. A fermer en
     * production : elle decrit l'integralite de la surface exposee, ce qui fait
     * gagner un temps considerable a qui cherche une faille.
     */
    private final boolean swaggerPublic;

    public SecurityConfig(@Value("${guardianai.cors.allowed-origins}") String allowedOrigins,
                          @Value("${guardianai.swagger.public:true}") boolean swaggerPublic) {
        this.allowedOrigins = allowedOrigins;
        this.swaggerPublic = swaggerPublic;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           RateLimitFilter rateLimitFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Sans session ni cookie d'authentification, il n'y a pas de requete
                // implicitement authentifiee par le navigateur : le vecteur CSRF
                // n'existe pas. L'identite doit etre presentee explicitement a chaque
                // appel, dans un en-tete que seul le code de l'application ajoute.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Connexion : necessairement ouverte.
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Requetes preliminaires CORS, emises sans en-tete d'autorisation.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Signal de vie : ne revele que « le service repond ». C'est le
                        // seul endpoint public en dehors de la connexion.
                        //
                        // L'etat detaille, lui, exige un compte : il expose la pile
                        // technique, les composants et leurs temps de reponse, donc les
                        // moments ou la plateforme est fragile.
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        // Documentation de l'API, ouverte en developpement seulement.
                        // Le parametre est nomme `identite` et non `auth` : ce dernier
                        // designe deja le constructeur de regles du bloc englobant.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .access((identite, contexte) -> new AuthorizationDecision(
                                swaggerPublic
                                        || identite.get() != null && identite.get().isAuthenticated()))
                        // Administration des comptes : creer, desactiver ou reinitialiser
                        // un acces ne releve pas du travail d'analyse.
                        .requestMatchers("/api/v1/utilisateurs/**").hasRole("ADMINISTRATEUR")
                        // Reserve a l'administration : la suppression d'une analyse
                        // touche a la tracabilite (RF-11).
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMINISTRATEUR")
                        .anyRequest().authenticated())

                // Sans cela, Spring redirigerait vers une page de connexion HTML.
                // Un client Angular attend un 401 exploitable, pas une redirection.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // Les deux filtres sont ancres sur le meme filtre standard, et non
                // l'un sur l'autre : Spring Security n'accepte comme point d'ancrage
                // que les filtres dont il connait la position. A position egale, il
                // respecte l'ordre d'ajout.
                //
                // Le quota passe donc en premier, avant l'authentification : une
                // tentative de connexion refusee doit compter, sinon la limite ne
                // protegerait que les requetes deja authentifiees — exactement
                // l'inverse du besoin.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt : algorithme lent par conception, ce qui rend couteuse toute tentative
     * de retrouver un mot de passe par force brute a partir de son empreinte.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
