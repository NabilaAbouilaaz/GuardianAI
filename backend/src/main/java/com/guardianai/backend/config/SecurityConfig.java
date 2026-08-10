package com.guardianai.backend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

    public SecurityConfig(@Value("${guardianai.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
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
                        // Etat des services : consultable sans compte, pour permettre
                        // une supervision externe et un diagnostic quand l'authentification
                        // elle-meme est en cause.
                        .requestMatchers(HttpMethod.GET, "/api/v1/status").permitAll()
                        // Reserve a l'administration : la suppression d'une analyse
                        // touche a la tracabilite (RF-11).
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMINISTRATEUR")
                        .anyRequest().authenticated())

                // Sans cela, Spring redirigerait vers une page de connexion HTML.
                // Un client Angular attend un 401 exploitable, pas une redirection.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

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
