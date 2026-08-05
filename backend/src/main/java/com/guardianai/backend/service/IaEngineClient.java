package com.guardianai.backend.service;

import com.guardianai.backend.dto.IaVerdict;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
        // SimpleClientHttpRequestFactory et non JdkClientHttpRequestFactory : ce dernier
        // s'appuie sur le HttpClient du JDK, qui annonce une taille fixe puis ecrit le
        // corps en flux. Sur un envoi multipart vers uvicorn, le serveur repond avant
        // d'avoir tout lu et ferme la connexion, ce qui provoque un
        // "Connection reset by peer" cote Java alors que le moteur est parfaitement
        // fonctionnel — verifie en appelant /predict directement.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
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
        return analyze(file, false);
    }

    /**
     * @param expliquer demande au moteur de joindre la decomposition SHAP.
     *                  Calculee dans la meme passe que la prediction, elle evite
     *                  une seconde extraction des caracteristiques.
     */
    public IaVerdict analyze(MultipartFile file, boolean expliquer) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileNotAnalyzableException("Lecture du fichier impossible.", e);
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "fichier";

        // On construit le corps multipart avec une MultiValueMap plutot qu'avec
        // MultipartBodyBuilder : cette derniere s'appuie sur reactive-streams, une
        // dependance qui n'a pas sa place dans une application servlet classique et
        // dont l'absence provoquait une NoClassDefFoundError a l'execution.
        // Un nom de fichier est indispensable ici : sans lui, la partie est transmise
        // comme un simple champ de formulaire et FastAPI la rejette.
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        try {
            return client.post()
                    .uri(builder -> builder.path("/predict")
                            .queryParam("expliquer", expliquer)
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(IaVerdict.class);

        } catch (RestClientResponseException e) {
            // Le moteur a repondu, mais en erreur : fichier illisible ou trop volumineux.
            int status = e.getStatusCode().value();
            log.warn("Moteur IA : refus de '{}' (HTTP {})", filename, status);
            throw new FileNotAnalyzableException(
                    "Le moteur n'a pas pu analyser ce fichier (code " + status + ").", e);

        } catch (Exception e) {
            // Moteur injoignable, arrete, delai depasse, ou echec de lecture de la
            // reponse. Ces situations sont distinctes mais aboutissent toutes ici ;
            // on journalise donc la trace complete et non le seul message, faute de
            // quoi la cause reelle est perdue.
            log.error("Echec de l'appel au moteur IA pour '{}'", filename, e);

            // Le detail technique est repris dans le message pour rester diagnosticable
            // en developpement. A remplacer par un message generique avant mise en
            // production, afin de ne pas exposer le fonctionnement interne.
            Throwable racine = e;
            while (racine.getCause() != null) {
                racine = racine.getCause();
            }

            throw new IaEngineUnavailableException(
                    "Echec de l'appel au moteur d'analyse : "
                            + racine.getClass().getSimpleName() + " — " + racine.getMessage(), e);
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
