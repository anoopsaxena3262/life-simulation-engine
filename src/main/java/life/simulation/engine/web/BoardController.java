package life.simulation.engine.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import life.simulation.engine.service.BoardService;
import life.simulation.engine.web.dto.BoardResponse;
import life.simulation.engine.web.dto.CreateBoardRequest;
import life.simulation.engine.web.dto.FinalStateResponse;
import life.simulation.engine.web.dto.GenerationResponse;

/**
 * The five endpoints from DESIGN.md section 5.
 *
 * <p>Every generation endpoint is a GET and none of them mutate the stored board.
 * {@code /next} delegates to the same path as {@code /generations/1} rather than
 * duplicating the logic; it exists because the brief names it as a separate capability.
 */
@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

    private static final Logger log = LoggerFactory.getLogger(BoardController.class);

    private final BoardService service;

    public BoardController(BoardService service) {
        this.service = service;
    }

    /** F1: upload a board. 201 with a Location header. */
    @PostMapping
    public ResponseEntity<BoardResponse> create(@Valid @RequestBody CreateBoardRequest request) {
        // TODO: service.create(...), then build the response from generation 0.
        // TODO: return ResponseEntity.created(URI.create("/api/v1/boards/" + id)).body(response)
        throw new UnsupportedOperationException("not implemented");
    }

    /** Board metadata and generation 0. */
    @GetMapping("/{id}")
    public BoardResponse get(@PathVariable UUID id) {
        // TODO: service.get(id), map to BoardResponse.
        throw new UnsupportedOperationException("not implemented");
    }

    /** F2: one generation forward. Delegates to generationAt(id, 1). */
    @GetMapping("/{id}/next")
    public GenerationResponse next(@PathVariable UUID id) {
        return generationAt(id, 1);
    }

    /** F3: the state n generations away. */
    @GetMapping("/{id}/generations/{n}")
    public GenerationResponse generationAt(@PathVariable UUID id, @PathVariable("n") int n) {
        // TODO: service.generationAt(id, n), deserialize, map to GenerationResponse.
        throw new UnsupportedOperationException("not implemented");
    }

    /** F4: final state, or 422 if the board does not conclude within the limit. */
    @GetMapping("/{id}/final")
    public FinalStateResponse finalState(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer maxGenerations) {
        // TODO: service.finalState(id, maxGenerations), map TerminationResult to the response.
        throw new UnsupportedOperationException("not implemented");
    }
}
