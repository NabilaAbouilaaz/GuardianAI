package com.guardianai.backend.service;

import com.guardianai.backend.dto.IaVerdict;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Connecteur vers le microservice d'analyse (FastAPI).
 *
 * Toute la communication avec le moteur IA passe par cette classe : si le contrat
 * d'API change, un seul fichier est a modifier cote backend.
 */
@Component
public class IaEngineClient {

    private static final Logger log = LoggerFactory.getLogger(IaEngineClient.class);

    private final RestClient client;

    public IaEngineClient(@Value("${guardianai.ia-engine.base-url}") String baseUrl,
                          @Value("${guardianai.ia-engine.timeout-seconds:10}") long timeoutSeconds) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        log.info("Moteur IA configure sur {} (delai max {} s)", baseUrl, timeoutSeconds);
    }

    /**
     * Envoie un fichier au moteur et retourne son verdict.
     *
     * @throws FileNotAnalyzableException si le moteur ne sait pas lire ce fichier
     * @throws IaEngineUnavailableException si le moteur ne repond pas
     */
    public IaVerdict analyze(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileNotAnalyzableException("Lecture du fichier impossible.", e);
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "fichier";

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        try {
            return client.post()
                    .uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(IaVerdict.class);

        } catch (RestClientResponseException e) {
            // Le moteur a repondu, mais en erreur : fichier illisible ou trop volumineux.
            int status = e.getStatusCode().value();
            log.warn("Moteur IA : refus de '{}' (HTTP {})", filename, status);
            throw new FileNotAnalyzableException(
                    "Le moteur n'a pas pu analyser ce fichier (code " + status + ").", e);

        } catch (Exception e) {
            // Moteur injoignable, arrete ou delai depasse.
            log.error("Moteur IA injoignable : {}", e.getMessage());
            throw new IaEngineUnavailableException(
                    "Le moteur d'analyse est indisponible. Reessayer plus tard.", e);
        }
    }

    /** Verifie que le moteur repond, pour la page d'etat des services. */
    public boolean isAvailable() {
        try {
            client.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Fichier recu mais impossible a analyser (format non gere, corrompu). */
    public static class FileNotAnalyzableException extends RuntimeException {
        public FileNotAnalyzableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Moteur d'analyse hors service ou trop lent. */
    public static class IaEngineUnavailableException extends RuntimeException {
        public IaEngineUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
