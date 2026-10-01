package com.example.gameoflife.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised limits. Bound from the {@code game-of-life} block in application.yml.
 *
 * @param maxGenerations        default cap on generations walked when resolving a final state
 * @param maxGenerationsCeiling hard ceiling a caller may not exceed via the query parameter
 * @param maxCells              upper bound on width * height for an uploaded board
 */
@ConfigurationProperties(prefix = "game-of-life")
public record GameProperties(
        int maxGenerations,
        int maxGenerationsCeiling,
        int maxCells) {
}
