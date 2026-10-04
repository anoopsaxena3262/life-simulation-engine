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

import life.simulation.engine.domain.Board;
import life.simulation.engine.domain.StateCodec;
import life.simulation.engine.service.BoardService;
import life.simulation.engine.service.FinalStateOutcome;
import life.simulation.engine.web.dto.BoardResponse;
import life.simulation.engine.web.dto.CreateBoardRequest;
import life.simulation.engine.web.dto.FinalStateResponse;
import life.simulation.engine.web.dto.GenerationResponse;

/**
 * The five endpoints from DESIGN.md section 5.
 *
 * <p>Every generation endpoint is a GET and none of them mutate the stored board.
 * {@code /next} uses the same read as {@code /generations/1}. It is a separate
 * mapping because the brief names it as its own capability, and it is logged as that call.
 */
@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

    private static final Logger log = LoggerFactory.getLogger(BoardController.class);

    private final BoardService service;

    public BoardController(BoardService service) {
        log.debug("BoardController created");
        this.service = service;
    }

    /** F1: upload a board. 201 with a Location header. */
    @PostMapping
    public ResponseEntity<BoardResponse> create(@Valid @RequestBody CreateBoardRequest request) {
        log.info("POST /boards width={} height={}", request.width(), request.height());
        UUID id = service.create(request.width(), request.height(), request.cells());
        Board board = service.get(id);
        boolean[][] cells = StateCodec.deserialize(board.initialState(), board.width(), board.height());
        BoardResponse response = new BoardResponse(id, board.width(), board.height(), 0, cells);
        return ResponseEntity.created(URI.create("/api/v1/boards/" + id)).body(response);
    }

    /** Board metadata and generation 0. */
    @GetMapping("/{id}")
    public BoardResponse get(@PathVariable UUID id) {
        log.info("GET /boards/{}", id);
        Board board = service.get(id);
        boolean[][] cells = StateCodec.deserialize(board.initialState(), board.width(), board.height());
        return new BoardResponse(id, board.width(), board.height(), 0, cells);
    }

    /** F2: one generation forward. Same read as {@code /generations/1}, logged as its own call. */
    @GetMapping("/{id}/next")
    public GenerationResponse next(@PathVariable UUID id) {
        log.info("GET /boards/{}/next", id);
        return generationResponse(id, 1);
    }

    /** F3: the state n generations away. */
    @GetMapping("/{id}/generations/{n}")
    public GenerationResponse generationAt(@PathVariable UUID id, @PathVariable("n") int n) {
        log.info("GET /boards/{}/generations/{}", id, n);
        return generationResponse(id, n);
    }

    private GenerationResponse generationResponse(UUID id, int n) {
        Board board = service.get(id);
        String state = service.generationAt(id, n);
        boolean[][] cells = StateCodec.deserialize(state, board.width(), board.height());
        return new GenerationResponse(id, board.width(), board.height(), n, cells);
    }

    /** F4: final state, or 422 if the board does not conclude within the limit. */
    @GetMapping("/{id}/final")
    public FinalStateResponse finalState(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer maxGenerations) {
        log.info("GET /boards/{}/final maxGenerations={}", id, maxGenerations);
        Board board = service.get(id);
        FinalStateOutcome outcome = service.finalState(id, maxGenerations);
        boolean[][] cells = StateCodec.deserialize(
                outcome.result().state(), board.width(), board.height());
        return new FinalStateResponse(
                id,
                board.width(),
                board.height(),
                cells,
                outcome.result().kind(),
                outcome.result().firstOccurrence(),
                outcome.result().period(),
                outcome.result().generationsComputed(),
                outcome.generationsLimit());
    }
}
