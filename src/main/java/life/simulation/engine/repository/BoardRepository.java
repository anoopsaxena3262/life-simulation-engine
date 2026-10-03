package life.simulation.engine.repository;

import java.util.Optional;
import java.util.UUID;

import life.simulation.engine.domain.Board;

/**
 * Storage contract for boards and their memoised generations.
 *
 * <p>This is the one single-implementation interface in the codebase, and it earns
 * that place: it is the stated extension point for moving off SQLite to a
 * server-backed store. See DESIGN.md section 3.1.
 */
public interface BoardRepository {

    void save(Board board);

    Optional<Board> findById(UUID id);

    /**
     * @return the memoised state at {@code index}, or empty if it has not been computed yet
     */
    Optional<String> findGeneration(UUID boardId, int index);

    /**
     * @return the highest generation index already cached for this board, or empty when
     *         only generation 0 exists -- lets the service resume rather than restart
     */
    Optional<Integer> findHighestCachedIndex(UUID boardId);

    /**
     * Idempotent. Two requests racing to compute the same generation produce the same
     * row, so the write is allowed to collide harmlessly. See DESIGN.md section 8.
     */
    void saveGeneration(UUID boardId, int index, String state);
}
