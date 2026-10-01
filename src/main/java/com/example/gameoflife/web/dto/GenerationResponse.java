package com.example.gameoflife.web.dto;

import java.util.UUID;

/** A single generation of a board. */
public record GenerationResponse(
        UUID id,
        int width,
        int height,
        int generation,
        boolean[][] cells) {
}
