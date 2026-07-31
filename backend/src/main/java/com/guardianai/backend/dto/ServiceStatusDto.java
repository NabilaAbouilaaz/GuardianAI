package com.guardianai.backend.dto;

/** Etat d'un composant de la plateforme, affiche sur la page System Status. */
public record ServiceStatusDto(String name, String status, String latency, String uptime) {
}
