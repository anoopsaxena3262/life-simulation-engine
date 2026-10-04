package life.simulation.engine.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import life.simulation.engine.config.GameProperties;
import life.simulation.engine.domain.Board;
import life.simulation.engine.domain.LifeEngine;
import life.simulation.engine.domain.StateCodec;
import life.simulation.engine.domain.TerminationDetector;
import life.simulation.engine.domain.TerminationResult;
import life.simulation.engine.repository.BoardRepository;
import life.simulation.engine.service.exception.BoardNotFoundException;
import life.simulation.engine.service.exception.InvalidBoardException;
import life.simulation.engine.service.exception.NoConclusionException;

/**
 * Orchestration, memoisation and limit policy.
 *
 * <p>Reads are pure. Fetching generation N never advances the stored board -- boards are
 * immutable after upload, and the only writes a GET performs go to the generation cache,
 * which is invisible through the API. This is what keeps the endpoints idempotent:
 * calling /next twice returns generation 1 both times.
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final BoardRepository repository;
    private final GameProperties properties;

    public BoardService(BoardRepository repository, GameProperties properties) {
        log.debug("BoardService maxGenerations={} ceiling={} maxCells={}",
                properties.maxGenerations(), properties.maxGenerationsCeiling(), properties.maxCells());
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Validates and stores a new board.
     *
     * @return the generated board id
     * @throws InvalidBoardException on a malformed grid
     */
    public UUID create(int width, int height, boolean[][] grid) {
        log.debug("create width={} height={} rows={}", width, height, grid == null ? null : grid.length);
        // Validate width > 0, height > 0, grid dimensions match, width * height <= maxCells.
        // Serialize via StateCodec, build the Board record with a random UUID and Instant.now().
        // repository.save(board), return the id.

        // Validates all constraints before proceeding
        if (width <= 0 || height <= 0) {
            throw new InvalidBoardException("Width and height must be positive");
        }
        if (grid == null || grid.length != height) {
            throw new InvalidBoardException("Grid height does not match declared height");
        }
        for (boolean[] row : grid) {
            if (row == null || row.length != width) {
                throw new InvalidBoardException("Grid width does not match declared width");
            }
        }
        if ((long) width * height > properties.maxCells()) {
            throw new InvalidBoardException("Board exceeds maximum cell count of " + properties.maxCells());
        }

        String initialState = StateCodec.serialize(grid);
        UUID id = UUID.randomUUID();
        // The last argument is the per-board generation cap. Upload does not accept one,
        // so it stays null and /final uses the query parameter or the configured default.
        Board board = new Board(id, width, height, initialState, Instant.now(), null);

        repository.save(board);
        log.info("created board id={} width={} height={}", id, width, height);
        return id;
    }

    public Board get(UUID id) {
        log.debug("get id={}", id);
        return repository.findById(id)
                .orElseThrow(() -> new BoardNotFoundException(id));
    }

    /**
     * Returns the state {@code index} generations after upload. Pure read.
     *
     * @param index 0 returns the uploaded board unchanged
     */
    public String generationAt(UUID id, int index) {
        log.debug("generationAt id={} index={}", id, index);

        // Rejects negative indices and indices above the ceiling
        if (index < 0) {
            throw new InvalidBoardException("Generation index cannot be negative");
        }
        if (index > properties.maxGenerationsCeiling()) {
            throw new InvalidBoardException(
                    "Generation index exceeds ceiling of " + properties.maxGenerationsCeiling());
        }

        // Generation cache hit check
        var cached = repository.findGeneration(id, index);
        if (cached.isPresent()) {
            log.debug("generationAt cache hit id={} index={}", id, index);
            return cached.get();
        }

        // Cache miss. Resume from the highest cached index that is still at or before
        // the one we were asked for. A later cached row is a different generation.
        Board board = get(id);
        long cells = (long) board.width() * board.height();
        if ((long) index * cells > properties.maxCellGenerations()) {
            throw new InvalidBoardException(
                    "Generation " + index + " on a board of " + cells
                            + " cells exceeds the cell-generation budget of "
                            + properties.maxCellGenerations());
        }
        int highest = repository.findHighestCachedIndex(id).orElse(0);
        int startIndex = highest;
        if (highest > index) {
            log.warn("generationAt id={} cached index {} is past requested {}; resuming from 0",
                    id, highest, index);
            startIndex = 0;
        }
        var start = repository.findGeneration(id, startIndex);
        String currentState;
        if (start.isPresent()) {
            currentState = start.get();
        } else if (startIndex == 0) {
            currentState = board.initialState();
        } else {
            throw new IllegalStateException(
                    "Generation cache for board " + id + " is missing index " + startIndex);
        }

        log.debug("generationAt computing id={} from={} to={}", id, startIndex, index);
        for (int step = startIndex; step < index; step++) {
            currentState = LifeEngine.step(currentState, board.width(), board.height());
            repository.saveGeneration(id, step + 1, currentState);
        }
        return currentState;
    }

    /**
     * Walks the board to a fixed point or a cycle.
     *
     * @param requestedMax caller override. Below 1 is rejected. Above the ceiling is clamped.
     *                     Null uses the board's stored cap, or the configured default when that is null.
     * @throws InvalidBoardException if {@code requestedMax} is below 1
     * @throws NoConclusionException if the limit is reached
     */
    public FinalStateOutcome finalState(UUID id, Integer requestedMax) {
        log.debug("finalState id={} requestedMax={}", id, requestedMax);

        Board board = get(id);
        int limit = resolveLimit(board, requestedMax);
        log.debug("finalState id={} limit={}", id, limit);

        TerminationResult result = TerminationDetector.detect(
                board.initialState(), board.width(), board.height(), limit)
                .orElseThrow(() -> new NoConclusionException(limit));
        log.info("finalState id={} kind={} period={} firstOccurrence={} generations={} limit={}",
                id, result.kind(), result.period(), result.firstOccurrence(),
                result.generationsComputed(), limit);
        return new FinalStateOutcome(result, limit);
    }

    /**
     * Caller override, then a stored per-board cap, then the configured default.
     * Upload never sets the stored cap, so the middle branch is for a row written outside this API.
     * The ceiling then clamps that number. It is visible as {@code generationsLimit}.
     * The cell-generation budget does not apply here. /final writes no rows; it only
     * keeps a hash per step. The budget is for /generations, which stores every state.
     */
    private int resolveLimit(Board board, Integer requestedMax) {
        if (requestedMax != null && requestedMax < 1) {
            throw new InvalidBoardException("maxGenerations must be at least 1");
        }
        int limit;
        if (requestedMax != null) {
            limit = requestedMax;
        } else if (board.maxGenerations() != null) {
            limit = board.maxGenerations();
        } else {
            limit = properties.maxGenerations();
        }
        if (requestedMax != null && requestedMax > properties.maxGenerationsCeiling()) {
            log.warn("finalState id={} maxGenerations {} clamped to ceiling {}",
                    board.id(), requestedMax, properties.maxGenerationsCeiling());
        }
        return Math.min(limit, properties.maxGenerationsCeiling());
    }
}
