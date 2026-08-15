package com.guardianai.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardianai.backend.domain.AppUser;
import com.guardianai.backend.repository.AppUserRepository;
import com.guardianai.backend.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 a decoupe ses modules de test : cette annotation vit desormais
// dans org.springframework.boot.webmvc.test.autoconfigure, et non plus sous
// org.springframework.boot.test.autoconfigure.web.servlet.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regles d'acces a l'API (RF-07).
 *
 * Ces tests protegent ce que les tests unitaires ne voient pas : la configuration
 * de securite. Une regle mal ecrite n'echoue jamais a la compilation — elle ouvre
 * silencieusement une ressource, ou en ferme une qui devait rester accessible.
 *
 * <p>Les jetons sont forges directement avec JwtService plutot qu'obtenus par une
 * connexion : les mots de passe des comptes de depart sont renouveles des la
 * premiere utilisation, un test ne peut donc pas s'appuyer dessus sans devenir
 * faux le lendemain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReglesAccesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AppUserRepository utilisateurs;

    private String jetonDe(String username) {
        AppUser u = utilisateurs.findByUsernameIgnoreCase(username).orElseThrow(
                () -> new IllegalStateException(
                        "Compte '" + username + "' absent : la migration V3 n'a pas ete appliquee."));
        return "Bearer " + jwtService.emettre(u);
    }

    @Nested
    @DisplayName("Sans authentification")
    class SansJeton {

        /**
         * Le seul endpoint public en dehors de la connexion. Il ne revele rien
         * d'autre que « le service repond » — ni la pile technique, ni les
         * composants, ni leurs temps de reponse.
         */
        @Test
        void leSignalDeVieEstAccessible() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        /**
         * Cet endpoint etait public dans une version anterieure. Il exposait les
         * composants et leurs latences, donc les moments ou la plateforme est
         * fragile. Ce test empeche la regression.
         */
        @Test
        @DisplayName("L'etat detaille des services est ferme")
        void lEtatDetailleEstRefuse() throws Exception {
            mockMvc.perform(get("/api/v1/status")).andExpect(status().isUnauthorized());
        }

        @Test
        void lHistoriqueEstRefuse() throws Exception {
            mockMvc.perform(get("/api/v1/scans/recent")).andExpect(status().isUnauthorized());
        }

        @Test
        void lesAlertesSontRefusees() throws Exception {
            mockMvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
        }

        @Test
        void lAdministrationDesComptesEstRefusee() throws Exception {
            mockMvc.perform(get("/api/v1/utilisateurs")).andExpect(status().isUnauthorized());
        }

        /** Un identifiant inconnu et un mot de passe errone donnent la meme reponse. */
        @Test
        void uneConnexionErroneeEstRefusee() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"inconnu\",\"password\":\"quelconque\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Avec un jeton d'analyste")
    class AvecAnalyste {

        @Test
        void accedeALHistorique() throws Exception {
            mockMvc.perform(get("/api/v1/scans/recent")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("analyste")))
                    .andExpect(status().isOk());
        }

        @Test
        void accedeALEtatDesServices() throws Exception {
            mockMvc.perform(get("/api/v1/status")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("analyste")))
                    .andExpect(status().isOk());
        }

        /**
         * Le test le plus important du fichier : creer ou desactiver un acces ne
         * releve pas du travail d'analyse. Si cette regle sautait, n'importe quel
         * analyste pourrait se promouvoir administrateur, et rien dans l'interface
         * ne le trahirait.
         */
        @Test
        @DisplayName("Ne peut pas administrer les comptes")
        void nAccedePasALAdministration() throws Exception {
            mockMvc.perform(get("/api/v1/utilisateurs")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("analyste")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void neCreePasDeCompte() throws Exception {
            mockMvc.perform(post("/api/v1/utilisateurs")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("analyste"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"intrus\",\"nom\":\"Intrus\","
                                    + "\"role\":\"ADMINISTRATEUR\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Avec un jeton d'administrateur")
    class AvecAdministrateur {

        @Test
        void accedeALAdministration() throws Exception {
            mockMvc.perform(get("/api/v1/utilisateurs")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("admin")))
                    .andExpect(status().isOk());
        }

        @Test
        void accedeAussiAuxVuesMetier() throws Exception {
            mockMvc.perform(get("/api/v1/alerts")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("admin")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Jetons invalides")
    class JetonsInvalides {

        @Test
        void unJetonFalsifieEstRejete() throws Exception {
            String jeton = jetonDe("admin");
            String altere = jeton.substring(0, 40) + "X" + jeton.substring(41);

            mockMvc.perform(get("/api/v1/utilisateurs")
                            .header(HttpHeaders.AUTHORIZATION, altere))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void uneChaineQuelconqueEstRejetee() throws Exception {
            mockMvc.perform(get("/api/v1/scans/recent")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer pas-un-jeton"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Sans le prefixe attendu, l'en-tete est ignore : la requete est traitee
         * comme anonyme, pas comme une tentative d'intrusion.
         */
        @Test
        void unEnteteSansPrefixeEstIgnore() throws Exception {
            mockMvc.perform(get("/api/v1/scans/recent")
                            .header(HttpHeaders.AUTHORIZATION, jetonDe("admin").substring(7)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Revocation immediate")
    class Revocation {

        /**
         * Une signature valide ne suffit pas. Sans cette verification, un compte
         * desactive garderait son acces complet jusqu'a l'expiration de son
         * jeton — le bouton « Desactiver » ne couperait rien dans l'immediat.
         *
         * <p>Le test cree son propre compte plutot que d'emprunter « analyste » :
         * revoquer les jetons d'un compte partage rendrait les autres tests
         * dependants de leur ordre d'execution, et donc instables.
         */
        @Test
        @DisplayName("Un compte desactive perd l'acces sans attendre l'expiration")
        void unCompteDesactivePerdSonAcces() throws Exception {
            AppUser jetable = new AppUser(
                    "temoin.revocation." + System.nanoTime(),
                    "Temoin de revocation",
                    "$2a$10$empreinte.sans.usage.ce.compte.ne.se.connecte.jamais",
                    AppUser.ROLE_ANALYSTE);
            utilisateurs.save(jetable);

            try {
                String jeton = "Bearer " + jwtService.emettre(jetable);

                // Le jeton fonctionne tant que le compte est actif.
                mockMvc.perform(get("/api/v1/scans/recent")
                                .header(HttpHeaders.AUTHORIZATION, jeton))
                        .andExpect(status().isOk());

                jetable.changerActivation(false);
                utilisateurs.save(jetable);

                // Le meme jeton, inchange et toujours valablement signe, est refuse.
                mockMvc.perform(get("/api/v1/scans/recent")
                                .header(HttpHeaders.AUTHORIZATION, jeton))
                        .andExpect(status().isUnauthorized());
            } finally {
                utilisateurs.delete(jetable);
            }
        }
    }
}
