package com.example.gameoflife.service.exception;

import java.util.UUID;

/** No board exists with the requested id. Surfaces as 404. */
public class BoardNotFoundException extends RuntimeException {

    public BoardNotFoundException(UUID id) {
        super("No board with id " + id);
    }
}
