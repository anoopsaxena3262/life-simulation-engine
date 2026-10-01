package com.example.gameoflife.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Upload payload.
 *
 * <pre>
 * { "width": 3, "height": 3, "cells": [[false,true,false],[false,true,false],[false,true,false]] }
 * </pre>
 *
 * @param cells row-major, outer array is rows
 */
public record CreateBoardRequest(
        @Min(1) int width,
        @Min(1) int height,
        @NotNull boolean[][] cells) {
}
