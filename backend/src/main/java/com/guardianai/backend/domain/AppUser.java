package com.guardianai.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Compte utilisateur de la plateforme (exigence RF-07).
 *
 * Deux roles seulement : ANALYSTE, qui consulte et analyse, et ADMINISTRATEUR,
 * qui dispose en plus des operations sensibles. La hierarchie etant lineaire,
 * un role unique par compte suffit ; une table d'association serait
 * disproportionnee pour deux valeurs.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    public static final String ROLE_ANALYSTE = "ANALYSTE";
    public static final String ROLE_ADMIN = "ADMINISTRATEUR";

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** Empreinte BCrypt. Le mot de passe en clair n'est jamais conserve. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Un mot de passe initial, necessairement transmis, doit etre renouvele. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {
        // requis par JPA
    }

    public AppUser(String username, String displayName, String passwordHash, String role) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean estAdministrateur() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean doitChangerSonMotDePasse() {
        return mustChangePassword;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /** Vrai tant que le compte est sous blocage temporaire. */
    public boolean estVerrouille() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * Enregistre un echec de connexion et verrouille le compte au-dela du seuil.
     *
     * Le blocage est temporaire et non definitif : un verrouillage permanent
     * transformerait la protection en moyen de nuisance, puisqu'il suffirait de
     * saisir de mauvais mots de passe pour priver quelqu'un de son acces.
     */
    public void enregistrerEchec(int seuil, Duration duree) {
        this.failedAttempts++;
        if (this.failedAttempts >= seuil) {
            this.lockedUntil = Instant.now().plus(duree);
            this.failedAttempts = 0;
        }
    }

    public void enregistrerConnexion() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void changerMotDePasse(String nouvelleEmpreinte) {
        this.passwordHash = nouvelleEmpreinte;
        this.mustChangePassword = false;
    }
}
