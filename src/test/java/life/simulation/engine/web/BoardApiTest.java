package life.simulation.engine.web;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import life.simulation.engine.web.dto.BoardResponse;
import life.simulation.engine.web.dto.CreateBoardRequest;
import life.simulation.engine.web.dto.FinalStateResponse;
import life.simulation.engine.web.dto.GenerationResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of every endpoint and the error paths exercised over HTTP.
 * Cell values for the blinker are asserted here. The rules suite still owns the
 * other patterns. This class uses its own database file, not data/game-of-life.db.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoardApiTest {

    private static final Path DATABASE = tempDatabase();

    /** Above Jackson's 8,000-byte first read, so a later read can fail inside {@code cells}. */
    private static final int MAX_REQUEST_BYTES = 12_000;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void isolateDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE.toAbsolutePath());
        registry.add("game-of-life.max-request-bytes", () -> Integer.toString(MAX_REQUEST_BYTES));
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/boards";
    }

    @Test
    @DisplayName("F1: POST /boards returns 201 with an id and a Location header")
    void createBoardReturnsIdAndLocation() {
        CreateBoardRequest request = new CreateBoardRequest(3, 3, blinker());
        ResponseEntity<BoardResponse> response = restTemplate.postForEntity(
                baseUrl(),
                request,
                BoardResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().width()).isEqualTo(3);
        assertThat(response.getBody().height()).isEqualTo(3);
        assertThat(response.getBody().generation()).isEqualTo(0);
        assertThat(response.getBody().cells()).isDeepEqualTo(blinker());
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).startsWith("/api/v1/boards/");
    }

    @Test
    @DisplayName("F2: GET /next returns the following generation")
    void nextReturnsFollowingGeneration() {
        UUID id = createBoard();
        ResponseEntity<GenerationResponse> response = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/next",
                GenerationResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().generation()).isEqualTo(1);
        assertThat(response.getBody().cells()).isDeepEqualTo(verticalBlinker());
    }

    @Test
    @DisplayName("GET /boards/{id} returns the uploaded board")
    void getReturnsUploadedBoard() {
        UUID id = createBoard();
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                baseUrl() + "/" + id,
                BoardResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().generation()).isEqualTo(0);
        assertThat(response.getBody().cells()).isDeepEqualTo(blinker());
    }

    @Test
    @DisplayName("F2: calling /next twice returns the same generation both times")
    void nextIsIdempotent() {
        UUID id = createBoard();
        ResponseEntity<GenerationResponse> first = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/next",
                GenerationResponse.class
        );
        ResponseEntity<GenerationResponse> second = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/next",
                GenerationResponse.class
        );

        // Record equality compares boolean[][] by identity. The two responses are
        // separate deserializations of the same cached generation, so compare by value.
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody())
                .usingRecursiveComparison()
                .isEqualTo(first.getBody());
    }

    @Test
    @DisplayName("F3: GET /generations/{n} returns the expected state")
    void generationsAtIndexReturnsExpectedState() {
        UUID id = createBoard();
        ResponseEntity<GenerationResponse> response = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/generations/2",
                GenerationResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().generation()).isEqualTo(2);
        assertThat(response.getBody().cells()).isDeepEqualTo(blinker());
    }

    @Test
    @DisplayName("F4: GET /final returns the state and termination metadata")
    void finalReturnsTerminationMetadata() {
        UUID id = createBoard();
        ResponseEntity<FinalStateResponse> response = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/final",
                FinalStateResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().terminationKind()).isNotNull();
        assertThat(response.getBody().period()).isGreaterThan(0);
        assertThat(response.getBody().generationsComputed()).isGreaterThan(0);
        assertThat(response.getBody().generationsLimit()).isEqualTo(1000);
        assertThat(response.getBody().cells()).isDeepEqualTo(blinker());
    }

    @Test
    @DisplayName("F4: GET /final returns 422 when the generation limit is exceeded")
    void finalReturns422WhenLimitExceeded() {
        UUID id = createBoard();
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/final?maxGenerations=1",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("No conclusion");
        assertThat(response.getBody()).contains("\"generationsAttempted\":1");
    }

    @Test
    @DisplayName("unknown board id returns 404 with a problem detail body")
    void unknownBoardReturns404() {
        UUID id = UUID.randomUUID();
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/" + id,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("Board not found");
    }

    @Test
    @DisplayName("malformed upload returns 400 with a problem detail body")
    void malformedUploadReturns400() {
        CreateBoardRequest request = new CreateBoardRequest(0, 3, new boolean[3][3]);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl(),
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Validation failed");
        assertThat(response.getBody()).contains("width");
    }

    @Test
    @DisplayName("a body that is not JSON keeps Spring's Bad Request title")
    void badJsonReturns400() {
        ResponseEntity<String> response = postJson("{");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Bad Request");
    }

    @Test
    @DisplayName("a missing cells field is a validation failure")
    void missingCellsReturns400() {
        ResponseEntity<String> response = postJson("{\"width\":1,\"height\":1}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Validation failed");
    }

    @Test
    @DisplayName("a null cell is rejected instead of stored as dead")
    void nullCellReturns400() {
        ResponseEntity<String> response = postJson("{\"width\":1,\"height\":1,\"cells\":[[null]]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a body over the request-size limit is rejected")
    void oversizedBodyReturns400() {
        ResponseEntity<String> response = postJson(
                "{\"width\":1,\"height\":1,\"cells\":[[" + " ".repeat(MAX_REQUEST_BYTES) + "]]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Request too large");
    }

    @Test
    @DisplayName("a chunked body that crosses the cap inside cells is Request too large")
    void chunkedOverflowInsideCellsIsRequestTooLarge() throws Exception {
        assertThat(MAX_REQUEST_BYTES).isGreaterThan(8_000);
        String prefix = "{\"width\":1,\"height\":1,\"cells\":[[";
        assertThat(prefix.length()).isLessThan(8_000);
        byte[] body = (prefix + " ".repeat(MAX_REQUEST_BYTES) + "]]}")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(body.length).isGreaterThan(MAX_REQUEST_BYTES);

        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl()).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setChunkedStreamingMode(0);
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }

        int status = connection.getResponseCode();
        InputStream error = connection.getErrorStream();
        String response = error == null ? "" : new String(error.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();

        assertThat(status).isEqualTo(400);
        assertThat(response).contains("Request too large");
    }

    @Test
    @DisplayName("negative generation index returns 400")
    void negativeIndexReturns400() {
        UUID id = createBoard();
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/" + id + "/generations/-1",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    private UUID createBoard() {
        CreateBoardRequest request = new CreateBoardRequest(3, 3, blinker());
        ResponseEntity<BoardResponse> response = restTemplate.postForEntity(
                baseUrl(),
                request,
                BoardResponse.class
        );
        return response.getBody().id();
    }

    private ResponseEntity<String> postJson(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(baseUrl(), new HttpEntity<>(json, headers), String.class);
    }

    private static Path tempDatabase() {
        try {
            return Files.createTempDirectory("life-simulation-engine").resolve("boards.db");
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static boolean[][] blinker() {
        return new boolean[][] {
                {false, false, false},
                {true, true, true},
                {false, false, false}
        };
    }

    private static boolean[][] verticalBlinker() {
        return new boolean[][] {
                {false, true, false},
                {false, true, false},
                {false, true, false}
        };
    }
}
