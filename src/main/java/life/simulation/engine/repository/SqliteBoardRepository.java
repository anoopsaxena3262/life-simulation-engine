package life.simulation.engine.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
        log.debug("SqliteBoardRepository created");
        this.jdbc = jdbc;
    }

    /**
     * Writes the board and generation 0 in one transaction. A crash between the two
     * inserts must not leave a board whose generation 0 row is missing.
     */
    @Override
    @Transactional
    public void save(Board board) {
        log.info("save board id={} width={} height={} maxGenerations={}",
                board.id(), board.width(), board.height(), board.maxGenerations());
        jdbc.sql("""
            INSERT INTO board (id, width, height, initial_state, created_at, max_generations)
            VALUES (?, ?, ?, ?, ?, ?)
            """)
            .param(board.id().toString())
            .param(board.width())
            .param(board.height())
            .param(board.initialState())
            .param(board.createdAt().toString())
            .param(board.maxGenerations())
            .update();

        jdbc.sql("""
            INSERT INTO generation (board_id, idx, state)
            VALUES (?, ?, ?)
            """)
            .param(board.id().toString())
            .param(0)
            .param(board.initialState())
            .update();

    }

    @Override
    public Optional<Board> findById(UUID id) {
        log.debug("findById id={}", id);
        return jdbc.sql("""
            SELECT id, width, height, initial_state, created_at, max_generations
            FROM board
            WHERE id = ?
            """)
            .param(id.toString())
            .query((rs, rowNum) -> {
                // getInt turns SQL NULL into 0. This column is optional, so keep null as null.
                int rawMax = rs.getInt("max_generations");
                Integer maxGenerations = rs.wasNull() ? null : rawMax;
                return new Board(
                        UUID.fromString(rs.getString("id")),
                        rs.getInt("width"),
                        rs.getInt("height"),
                        rs.getString("initial_state"),
                        Instant.parse(rs.getString("created_at")),
                        maxGenerations);
            })
            .optional();
    }

    @Override
    public Optional<String> findGeneration(UUID boardId, int index) {
        log.debug("findGeneration boardId={} index={}", boardId, index);
        return jdbc.sql("""
            SELECT state
            FROM generation
            WHERE board_id = ? AND idx = ?
            """)
            .param(boardId.toString())
            .param(index)
            .query(String.class)
            .optional();
    }

    @Override
    public Optional<Integer> findHighestCachedIndex(UUID boardId) {
        log.debug("findHighestCachedIndex boardId={}", boardId);
        //  This method finds the highest generation index that has been computed and cached.
        // Generation 0 is written with the board, so it is not a computed generation.
        // MAX over no computed rows returns NULL, so map that to Optional.empty().
        // empty means the service starts again from generation 0.
        // MAX always yields one row. The value is null when nothing above generation 0 is stored.
        // single() rejects that null, so read the row and wrap it.
        Integer highest = jdbc.sql("""
            SELECT MAX(idx)
            FROM generation
            WHERE board_id = ? AND idx > 0
            """)
            .param(boardId.toString())
            .query((rs, rowNum) -> (Integer) rs.getObject(1))
            .list()
            .getFirst();
        return Optional.ofNullable(highest);
    }

    @Override
    public void saveGeneration(UUID boardId, int index, String state) {
        log.debug("saveGeneration boardId={} index={} stateLength={}",
                boardId, index, state == null ? null : state.length());
        // IGNORE, not REPLACE. Two requests can compute the same generation at once.
        // The row for a given (board_id, idx) never changes, so the second insert is a no-op.
        jdbc.sql("""
            INSERT OR IGNORE INTO generation (board_id, idx, state)
            VALUES (?, ?, ?)
            """)
            .param(boardId.toString())
            .param(index)
            .param(state)
            .update();
    }
}
