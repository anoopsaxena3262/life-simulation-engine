package life.simulation.engine.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import life.simulation.engine.config.GameProperties;
import life.simulation.engine.domain.Board;
import life.simulation.engine.domain.TerminationKind;
import life.simulation.engine.repository.BoardRepository;
import life.simulation.engine.service.exception.BoardNotFoundException;
import life.simulation.engine.service.exception.InvalidBoardException;
import life.simulation.engine.service.exception.NoConclusionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    private static final String HORIZONTAL = "000111000";
    private static final String VERTICAL = "010010010";

    @Mock
    private BoardRepository repository;

    private BoardService service;

    @BeforeEach
    void setUp() {
        service = new BoardService(repository, limits(10, 100, 9));
    }

    @Test
    @DisplayName("rejects a grid whose dimensions do not match the declared width and height")
    void rejectsDimensionMismatch() {
        assertThatThrownBy(() -> service.create(0, 2, new boolean[2][2]))
                .isInstanceOf(InvalidBoardException.class);
        assertThatThrownBy(() -> service.create(2, 0, new boolean[1][2]))
                .isInstanceOf(InvalidBoardException.class);
        assertThatThrownBy(() -> service.create(2, 2, null))
                .isInstanceOf(InvalidBoardException.class);
        assertThatThrownBy(() -> service.create(2, 2, new boolean[1][2]))
                .isInstanceOf(InvalidBoardException.class);

        boolean[][] nullRow = {new boolean[2], null};
        assertThatThrownBy(() -> service.create(2, 2, nullRow))
                .isInstanceOf(InvalidBoardException.class);

        boolean[][] shortRow = {new boolean[2], new boolean[1]};
        assertThatThrownBy(() -> service.create(2, 2, shortRow))
                .isInstanceOf(InvalidBoardException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a board exceeding the configured cell cap")
    void rejectsOversizedBoard() {
        assertThatThrownBy(() -> service.create(4, 3, new boolean[3][4]))
                .isInstanceOf(InvalidBoardException.class)
                .hasMessageContaining("9");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("generation 0 returns the uploaded board unchanged")
    void generationZeroIsTheUploadedBoard() {
        UUID id = service.create(3, 3, blinker());
        ArgumentCaptor<Board> saved = ArgumentCaptor.forClass(Board.class);
        verify(repository).save(saved.capture());
        Board board = saved.getValue();

        assertThat(board.id()).isEqualTo(id);
        assertThat(board.initialState()).isEqualTo(HORIZONTAL);
        assertThat(board.maxGenerations()).isNull();

        when(repository.findGeneration(id, 0)).thenReturn(Optional.empty());
        when(repository.findById(id)).thenReturn(Optional.of(board));
        when(repository.findHighestCachedIndex(id)).thenReturn(Optional.empty());

        assertThat(service.generationAt(id, 0)).isEqualTo(HORIZONTAL);
        verify(repository, never()).saveGeneration(any(), anyInt(), any());
    }

    @Test
    @DisplayName("repeated reads of the same generation return the same state")
    void readsAreIdempotent() {
        UUID id = UUID.randomUUID();
        Board board = board(id, null);
        Map<Integer, String> cache = new HashMap<>();
        cache.put(0, HORIZONTAL);
        when(repository.findById(id)).thenReturn(Optional.of(board));
        when(repository.findGeneration(eq(id), anyInt()))
                .thenAnswer(invocation -> Optional.ofNullable(cache.get(invocation.getArgument(1))));
        when(repository.findHighestCachedIndex(id)).thenAnswer(invocation ->
                cache.keySet().stream().filter(index -> index > 0).max(Integer::compareTo));
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(repository).saveGeneration(eq(id), anyInt(), any());

        String first = service.generationAt(id, 1);
        String second = service.generationAt(id, 1);

        assertThat(first).isEqualTo(VERTICAL).isEqualTo(second);
        assertThat(cache).containsEntry(1, VERTICAL);
    }

    @Test
    @DisplayName("a cached generation is served without recomputation")
    void servesFromCache() {
        UUID id = UUID.randomUUID();
        when(repository.findGeneration(id, 1)).thenReturn(Optional.of(VERTICAL));

        assertThat(service.generationAt(id, 1)).isEqualTo(VERTICAL);

        verify(repository, never()).saveGeneration(any(), anyInt(), any());
        verify(repository, never()).findHighestCachedIndex(any());
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("resumes from the highest cached generation rather than from zero")
    void resumesFromCache() {
        UUID id = UUID.randomUUID();
        when(repository.findGeneration(id, 2)).thenReturn(Optional.empty());
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));
        when(repository.findHighestCachedIndex(id)).thenReturn(Optional.of(1));
        when(repository.findGeneration(id, 1)).thenReturn(Optional.of(VERTICAL));

        assertThat(service.generationAt(id, 2)).isEqualTo(HORIZONTAL);

        verify(repository).saveGeneration(id, 2, HORIZONTAL);
        verify(repository, never()).saveGeneration(eq(id), eq(1), any());
    }

    @Test
    @DisplayName("a gap below the highest cached index is not answered with that later state")
    void doesNotTreatALaterCachedGenerationAsAnEarlierOne() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));
        when(repository.findGeneration(id, 1)).thenReturn(Optional.empty());
        when(repository.findHighestCachedIndex(id)).thenReturn(Optional.of(4));
        when(repository.findGeneration(id, 0)).thenReturn(Optional.of(HORIZONTAL));

        assertThat(service.generationAt(id, 1)).isEqualTo(VERTICAL);
        verify(repository).saveGeneration(id, 1, VERTICAL);
    }

    @Test
    @DisplayName("clamps a caller-supplied generation limit to the configured ceiling")
    void clampsRequestedLimit() {
        service = new BoardService(repository, limits(1, 1, 9));
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));

        assertThatThrownBy(() -> service.finalState(id, 50))
                .isInstanceOf(NoConclusionException.class)
                .extracting(ex -> ((NoConclusionException) ex).getGenerationsAttempted())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a non-positive maxGenerations instead of reporting 422")
    void rejectsANonPositiveRequestedLimit() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));

        assertThatThrownBy(() -> service.finalState(id, 0))
                .isInstanceOf(InvalidBoardException.class);
        assertThatThrownBy(() -> service.finalState(id, -3))
                .isInstanceOf(InvalidBoardException.class);
    }

    @Test
    void usesTheBoardLimitWhenTheCallerDoesNotSupplyOne() {
        service = new BoardService(repository, limits(10, 100, 9));
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(board(id, 1)));

        assertThatThrownBy(() -> service.finalState(id, null))
                .isInstanceOf(NoConclusionException.class)
                .extracting(ex -> ((NoConclusionException) ex).getGenerationsAttempted())
                .isEqualTo(1);
    }

    @Test
    void usesTheConfiguredDefaultAndReturnsAConclusion() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));

        var result = service.finalState(id, null);

        assertThat(result.result().kind()).isEqualTo(TerminationKind.CYCLE);
        assertThat(result.result().period()).isEqualTo(2);
        assertThat(result.generationsLimit()).isEqualTo(10);
    }

    @Test
    void rejectsANegativeIndexAndAnIndexPastTheCeiling() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.generationAt(id, -1))
                .isInstanceOf(InvalidBoardException.class);
        assertThatThrownBy(() -> service.generationAt(id, 101))
                .isInstanceOf(InvalidBoardException.class);
        verify(repository, never()).findGeneration(any(), anyInt());
    }

    @Test
    void missingBoardAndMissingResumePoint() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(BoardNotFoundException.class);

        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));
        when(repository.findGeneration(id, 2)).thenReturn(Optional.empty());
        when(repository.findHighestCachedIndex(id)).thenReturn(Optional.of(1));
        when(repository.findGeneration(id, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generationAt(id, 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a walk whose cells times generations exceed the budget")
    void rejectsAWalkPastTheCellGenerationBudget() {
        service = new BoardService(repository, limits(10, 100, 9, 9));
        UUID id = UUID.randomUUID();
        when(repository.findGeneration(id, 2)).thenReturn(Optional.empty());
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));

        assertThatThrownBy(() -> service.generationAt(id, 2))
                .isInstanceOf(InvalidBoardException.class)
                .hasMessageContaining("budget");
        verify(repository, never()).saveGeneration(any(), anyInt(), any());
    }

    @Test
    @DisplayName("final state uses the generation cap, not the cell-generation budget")
    void finalStateIgnoresTheCellGenerationBudget() {
        service = new BoardService(repository, limits(10, 100, 9, 9));
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(board(id, null)));

        var outcome = service.finalState(id, null);

        assertThat(outcome.generationsLimit()).isEqualTo(10);
        assertThat(outcome.result().kind()).isEqualTo(TerminationKind.CYCLE);
    }

    @Test
    void getReturnsTheStoredBoard() {
        UUID id = UUID.randomUUID();
        Board board = board(id, null);
        when(repository.findById(id)).thenReturn(Optional.of(board));

        assertThat(service.get(id)).isEqualTo(board);
    }

    private static GameProperties limits(int maxGenerations, int ceiling, int maxCells) {
        return limits(maxGenerations, ceiling, maxCells, 1_000_000L);
    }

    private static GameProperties limits(int maxGenerations, int ceiling, int maxCells, long cellGenerations) {
        return new GameProperties(maxGenerations, ceiling, maxCells, cellGenerations, 2_000_000);
    }

    private static Board board(UUID id, Integer maxGenerations) {
        return new Board(id, 3, 3, HORIZONTAL, Instant.parse("2026-01-02T03:04:05Z"), maxGenerations);
    }

    private static boolean[][] blinker() {
        return new boolean[][] {
                {false, false, false},
                {true, true, true},
                {false, false, false}
        };
    }
}
