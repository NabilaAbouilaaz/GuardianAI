package com.guardianai.backend.repository;

import com.guardianai.backend.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Recherche insensible a la casse : un utilisateur qui saisit "Admin" doit
     * pouvoir se connecter au compte "admin". La contrainte d'unicite reste
     * exacte en base, ce qui suffit tant que les comptes sont crees en minuscules.
     */
    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
