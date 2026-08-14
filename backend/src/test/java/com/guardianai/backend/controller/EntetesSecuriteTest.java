package com.guardianai.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * En-tetes de securite accompagnant les reponses de l'API.
 *
 * Ces en-tetes sont invisibles a l'usage : personne ne les remarque tant qu'ils
 * sont la, et leur disparition ne casse rien. C'est precisement pour cela qu'ils
 * meritent des tests — une reconfiguration malheureuse passerait inapercue.
 *
 * <p><b>Portee.</b> Ils accompagnent les reponses du backend, c'est-a-dire du
 * JSON. Tant que l'interface Angular est servie par son propre serveur, la
 * politique de contenu qui protege les pages vient de la. Ces en-tetes prennent
 * tout leur sens le jour ou le backend sert le frontend compile.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntetesSecuriteTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Une politique de contenu est declaree")
    void declareUnePolitiqueDeContenu() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("object-src 'none'")));
    }

    /**
     * Sans cet en-tete, un navigateur peut deviner le type d'une reponse a partir
     * de son contenu. Une reponse JSON contenant du HTML pourrait alors etre
     * interpretee comme une page, et le script qu'elle porte execute.
     */
    @Test
    @DisplayName("Le type declare des reponses ne peut pas etre devine")
    void interditLaDeductionDuTypeDeContenu() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /** Protege du detournement de clic : la reponse ne peut pas etre encadree. */
    @Test
    void interditLEncadrement() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("L'URL complete n'est pas transmise aux sites tiers")
    void limiteLaTransmissionDeLUrl() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("Camera, micro et position sont refuses")
    void refuseLesFonctionsSensiblesDuNavigateur() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("Permissions-Policy",
                        Matchers.containsString("camera=()")));
    }
}
