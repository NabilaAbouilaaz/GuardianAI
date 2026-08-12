package com.guardianai.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation de l'API, generee depuis le code.
 *
 * Une documentation redigee a part diverge des le premier changement d'endpoint ;
 * celle-ci est construite a partir des annotations reellement presentes, elle ne
 * peut donc pas mentir sur ce qui est expose.
 *
 * Consultable sur /swagger-ui.html une fois le backend demarre.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI guardianaiOpenAPI() {
        // Declare l'authentification par jeton pour que l'interface d'essai
        // propose un champ ou le coller. Sans cela, toute tentative depuis
        // Swagger renverrait 401 sans explication.
        SecurityScheme jwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Jeton obtenu via POST /api/v1/auth/login.");

        return new OpenAPI()
                .info(new Info()
                        .title("GuardianAI — API d'analyse de fichiers")
                        .version("v1")
                        .description("""
                                Detection de malwares par apprentissage automatique.

                                Le backend orchestre l'analyse : il recoit un fichier, le transmet au
                                moteur LightGBM, conserve le verdict et sa justification, puis les met
                                a disposition de l'interface.

                                **Authentification.** Tous les endpoints exigent un jeton, a l'exception
                                de la connexion et de l'etat des services. Ce dernier reste ouvert pour
                                permettre un diagnostic lorsque l'authentification elle-meme est en cause.

                                **Verdicts.** CLEAN, SUSPICIOUS ou MALICIOUS, determines par un seuil
                                calibre pour ne pas depasser 2 % de faux positifs (RNF-03).

                                **Tracabilite.** Les analyses ne sont jamais supprimees et conservent
                                l'identite de leur auteur ainsi que la decomposition SHAP du verdict
                                (RF-11).""")
                        .contact(new Contact().name("Nabila Abouilaaz")))
                .components(new Components().addSecuritySchemes("bearer-jwt", jwt))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
