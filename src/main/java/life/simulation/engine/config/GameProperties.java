package life.simulation.engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised limits. Bound from the {@code game-of-life} block in application.yml.
 *
 * @param maxGenerations         default cap on generations walked when resolving a final state
 * @param maxGenerationsCeiling  hard ceiling a caller may not exceed via the query parameter
 * @param maxCells               upper bound on width * height for an uploaded board
 * @param maxCellGenerations     upper bound on cells × generations for one /generations read
 * @param maxRequestBytes        upper bound on a request body, checked before the grid is built
 */
@ConfigurationProperties(prefix = "game-of-life")
public record GameProperties(
        int maxGenerations,
        int maxGenerationsCeiling,
        int maxCells,
        long maxCellGenerations,
        int maxRequestBytes) {

    private static final Logger log = LoggerFactory.getLogger(GameProperties.class);

    public GameProperties {
        if (maxGenerations < 1) {
            throw new IllegalArgumentException("game-of-life.max-generations must be at least 1");
        }
        if (maxGenerationsCeiling < maxGenerations) {
            throw new IllegalArgumentException(
                    "game-of-life.max-generations-ceiling must be at least max-generations");
        }
        if (maxCells < 1) {
            throw new IllegalArgumentException("game-of-life.max-cells must be at least 1");
        }
        if (maxCellGenerations < 1) {
            throw new IllegalArgumentException("game-of-life.max-cell-generations must be at least 1");
        }
        if (maxRequestBytes < 1) {
            throw new IllegalArgumentException("game-of-life.max-request-bytes must be at least 1");
        }
        log.info("limits maxGenerations={} ceiling={} maxCells={} maxCellGenerations={} maxRequestBytes={}",
                maxGenerations, maxGenerationsCeiling, maxCells, maxCellGenerations, maxRequestBytes);
    }
}
