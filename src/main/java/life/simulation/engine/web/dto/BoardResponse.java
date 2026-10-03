package life.simulation.engine.web.dto;

import java.util.UUID;

/** Board metadata plus generation 0. */
public record BoardResponse(
        UUID id,
        int width,
        int height,
        int generation,
        boolean[][] cells) {
}
