package com.guardianai.backend.dto;

/** Repartition des analyses par extension de fichier. */
public record FileTypeCountDto(String type, long count) {
}
