package life.simulation.engine.repository;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import life.simulation.engine.domain.Board;

/**
 * SQLite-backed implementation. Plain JDBC rather than JPA: two tables do not
 * justify an ORM, and SQLite's Hibernate dialect support is community-maintained.
 */
@Repository
public class SqliteBoardRepository implements BoardRepository {

    private static final Logger log = LoggerFactory.getLogger(SqliteBoardRepository.class);

    private final JdbcClient jdbc;

    public SqliteBoardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Board board) {
        // TODO: INSERT INTO board (id, width, height, initial_state, created_at, max_generations)
        // TODO: store id as TEXT (board.id().toString()) and created_at as ISO-8601.
        // TODO: also write generation 0 into the generation table so lookups are uniform.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<Board> findById(UUID id) {
        // TODO: SELECT ... FROM board WHERE id = :id
        // TODO: map with a RowMapper into the Board record; use .optional() on the query.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<String> findGeneration(UUID boardId, int index) {
        // TODO: SELECT state FROM generation WHERE board_id = :boardId AND idx = :idx
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<Integer> findHighestCachedIndex(UUID boardId) {
        // TODO: SELECT MAX(idx) FROM generation WHERE board_id = :boardId
        // TODO: MAX over an empty set returns NULL, so map that to Optional.empty().
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void saveGeneration(UUID boardId, int index, String state) {
        // TODO: INSERT OR IGNORE INTO generation (board_id, idx, state) VALUES (...)
        // TODO: OR IGNORE is what makes the concurrent-computation race benign -- rows are
        // TODO: immutable for a given (board_id, idx), so either writer produces the same row.
        throw new UnsupportedOperationException("not implemented");
    }
}
