package com.guardianai.backend.service;

import com.guardianai.backend.domain.AppUser;
import com.guardianai.backend.dto.CreerUtilisateurRequest;
import com.guardianai.backend.dto.UtilisateurCreeResponse;
import com.guardianai.backend.dto.UtilisateurDto;
import com.guardianai.backend.repository.AppUserRepository;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administration des comptes (RF-07). */
@Service
public class UtilisateurService {

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * Alphabet du mot de passe initial.
     *
     * Les caracteres ambigus sont exclus — 0 et O, 1 et l et I — parce que ce mot
     * de passe est destine a etre lu puis retape, souvent depuis un message ou un
     * papier. Une confusion visuelle se solderait par un echec de connexion
     * incomprehensible.
     */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LONGUEUR_INITIALE = 16;

    private final AppUserRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final SecureRandom aleatoire = new SecureRandom();

    public UtilisateurService(AppUserRepository utilisateurs, PasswordEncoder encodeur) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> lister() {
        return utilisateurs.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername))
                .map(u -> new UtilisateurDto(
                        u.getId().toString(),
                        u.getUsername(),
                        u.getDisplayName(),
                        u.getRole(),
                        u.isEnabled(),
                        u.doitChangerSonMotDePasse(),
                        u.getLastLoginAt() == null ? null : HORODATAGE.format(u.getLastLoginAt())))
                .toList();
    }

    /**
     * Cree un compte et retourne son mot de passe initial.
     *
     * Le mot de passe est genere par le serveur plutot que choisi par
     * l'administrateur : celui-ci aurait tendance a reutiliser le meme pour tous
     * les comptes. Il est affiche une seule fois, n'est jamais stocke en clair,
     * et son renouvellement est impose des la premiere connexion.
     */
    @Transactional
    public UtilisateurCreeResponse creer(CreerUtilisateurRequest demande) {
        String username = demande.username() == null ? "" : demande.username().trim().toLowerCase(Locale.ROOT);

        if (username.length() < 3) {
            throw new IllegalArgumentException("L'identifiant doit faire au moins 3 caracteres.");
        }
        if (!username.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "L'identifiant ne peut contenir que des lettres, chiffres, point, tiret ou tiret bas.");
        }
        if (utilisateurs.findByUsernameIgnoreCase(username).isPresent()) {
            throw new IllegalArgumentException("Cet identifiant est deja utilise.");
        }

        String nom = demande.nom() == null || demande.nom().isBlank() ? username : demande.nom().trim();
        String role = demande.role();
        if (!AppUser.ROLE_ANALYSTE.equals(role) && !AppUser.ROLE_ADMIN.equals(role)) {
            throw new IllegalArgumentException(
                    "Role inconnu : " + role + ". Attendu : " + AppUser.ROLE_ANALYSTE
                            + " ou " + AppUser.ROLE_ADMIN);
        }

        String motDePasse = genererMotDePasse();
        AppUser cree = new AppUser(username, nom, encodeur.encode(motDePasse), role);
        cree.imposerChangementDeMotDePasse();
        utilisateurs.save(cree);

        return new UtilisateurCreeResponse(username, nom, role, motDePasse);
    }

    /**
     * Active ou desactive un compte.
     *
     * On ne supprime pas : les analyses conservent l'identifiant de leur auteur,
     * et effacer le compte rendrait ces traces anonymes retroactivement, ce que
     * la tracabilite interdit (RF-11).
     */
    @Transactional
    public void changerActivation(UUID id, boolean actif, UUID demandeur) {
        if (id.equals(demandeur) && !actif) {
            throw new IllegalArgumentException(
                    "Vous ne pouvez pas desactiver votre propre compte.");
        }

        AppUser u = utilisateurs.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Compte introuvable : " + id));

        if (!actif && u.estAdministrateur() && compterAdministrateursActifs() <= 1) {
            throw new IllegalArgumentException(
                    "Impossible de desactiver le dernier administrateur actif.");
        }

        u.changerActivation(actif);
        utilisateurs.save(u);
    }

    /** Reinitialise le mot de passe d'un compte et en retourne un nouveau. */
    @Transactional
    public UtilisateurCreeResponse reinitialiserMotDePasse(UUID id) {
        AppUser u = utilisateurs.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Compte introuvable : " + id));

        String motDePasse = genererMotDePasse();
        u.changerMotDePasse(encodeur.encode(motDePasse));
        u.imposerChangementDeMotDePasse();
        u.debloquer();
        utilisateurs.save(u);

        return new UtilisateurCreeResponse(u.getUsername(), u.getDisplayName(), u.getRole(), motDePasse);
    }

    private long compterAdministrateursActifs() {
        return utilisateurs.findAll().stream()
                .filter(AppUser::estAdministrateur)
                .filter(AppUser::isEnabled)
                .count();
    }

    /**
     * Mot de passe initial aleatoire.
     *
     * SecureRandom et non Random : ce dernier est previsible a partir de quelques
     * tirages, ce qui permettrait de deviner les mots de passe des comptes crees
     * ensuite. La boucle garantit au moins une lettre et un chiffre, exigences de
     * la politique — un mot de passe genere qui serait refuse a la connexion
     * serait absurde.
     */
    private String genererMotDePasse() {
        while (true) {
            StringBuilder sb = new StringBuilder(LONGUEUR_INITIALE);
            for (int i = 0; i < LONGUEUR_INITIALE; i++) {
                sb.append(ALPHABET.charAt(aleatoire.nextInt(ALPHABET.length())));
            }
            String candidat = sb.toString();
            if (candidat.chars().anyMatch(Character::isDigit)
                    && candidat.chars().anyMatch(Character::isLetter)) {
                return candidat;
            }
        }
    }
}
