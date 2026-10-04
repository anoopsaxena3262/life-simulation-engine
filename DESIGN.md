# Design — Conway's Game of Life - life-simulation-engine

**Stack:** Java 25 · Spring Boot 3.x · Maven · SQLite · JUnit 5 + AssertJ

This document records the design of Conway's Game of Life - life-simulation-engine and the reasoning behind each decision. It is written to be read alongside the code.

---

## 1. Requirements

### 1.1 Functional

| # | Capability | Notes |
|---|---|---|
| F1 | Upload a new board state, return board id | Id is server-generated |
| F2 | Get the next state for a board | One generation forward |
| F3 | Get the state N generations away | N supplied by caller |
| F4 | Get the final state | Error if no conclusion within a bounded number of attempts |

### 1.2 Non-functional

The service has to survive a restart or a crash and still have the boards. The code has to be complete, and that completeness has to be something you can show. Authentication and authorisation are not part of this service.

- **Durability.** Board rows and cached generations are committed to a SQLite file with WAL. Stopping the process with Ctrl+C, or killing it, leaves that file. The next process opens the same file. `RestartPersistenceTest` starts a context, writes a board and generations 0 through 4, shuts that context down, starts a new one on the same file, and reads the rows back. It does not recompute a missing cache, so a lost write would fail the test. `./try-restart.sh save`, then stop the server, then `./try-restart.sh check`, is the same proof against the process you started with `mvn spring-boot:run`.
- **Completeness.** Every behaviour in this document has a named test in §6. `./try-all.sh` runs the HTTP scenarios against a live process and stops on the first failure. `mvn test` is the full suite, including rules, storage, and the restart. JaCoCo reports coverage on `domain` and `service`.
- **No authentication or authorisation.** There is no security dependency, no login, and no token on any endpoint. Callers who can reach the port can call the API.
- Deterministic results, predictable performance on boards up to 300 cells per side, and clear error semantics. The caps that enforce that are in §1.4.

### 1.3 Out of scope

Authentication, authorisation, multi-tenancy, board deletion and listing, UI, horizontal scale-out, streaming updates. Each omission is deliberate; the notes below explain where the extension points are.

### 1.4 Resource bounds

`/generations/{n}` and `/final` spend different resources, so they have different caps.

| Limit | Default | What it stops |
|---|---|---|
| `max-cells` | 90,000 (300 per side) | An upload whose `width * height` is larger. Checked after the body is parsed. |
| `max-request-bytes` | 2,000,000 | A body larger than that, before Jackson builds the grid. A declared `Content-Length` over the cap is rejected immediately. A body with no declared length is counted as it is read. Crossing the cap inside a JSON property is still `Request too large`: Jackson wraps that failure, and the handler unwraps it. |
| `max-generations` | 1,000 | The default `/final` walk when the caller does not pass `maxGenerations` and the board has no stored cap. |
| `max-generations-ceiling` | 10,000 | The largest index `/generations/{n}` will compute, and the largest `maxGenerations` a caller can ask for. A higher value is clamped, not rejected. The ceiling is at least the default, so a mis-set ceiling cannot silently shrink the default. |
| `max-cell-generations` | 5,000,000 | Cells × generations for one `/generations/{n}` computation. That endpoint writes one state string per step. A 300×300 board can store `floor(5_000_000 / 90_000) = 55` generations. |

A state is a string of length `width * height`. `/final` writes nothing. It keeps a fingerprint of each step, and it compares the full string only when two fingerprints match (§4.3). Confirming a match replays from the nearest checkpoint, every 256 generations, rather than from generation 0. Its cap is the generation ceiling: a caller who asks for more than 10,000 is clamped to 10,000, and a caller who asks for nothing walks the default of 1,000. That default is enough for the small boards in the scripts. A glider in the corner of a 300×300 board is still moving at generation 1,000, so the default `/final` is a 422; asking for up to the ceiling reaches the corner still life at generation 1,192. The product budget does not apply to `/final`.

There is no request-processing timeout. A `/generations` walk that fits the product cap, and a `/final` walk inside the generation ceiling, are allowed to run to the end.

A null cell is rejected. Jackson is configured with `FAIL_ON_NULL_FOR_PRIMITIVES`, so `[[null, true]]` is a 400 rather than a board with that cell stored as dead.

---

## 2. Design decisions

The specification leaves several behaviours open. This section records how each was resolved and why.

| Question | Decision | Rationale |
|---|---|---|
| Grid topology — finite, infinite, or toroidal? | **Finite, fixed-size grid with dead borders.** Width and height are fixed at upload. | Bounded memory and a bounded state space, which makes cycle detection tractable. An infinite grid is a possible extension, not a requirement. |
| What constitutes a "final state"? | A board has concluded when it reaches a **fixed point** (the next generation is identical) or enters a **cycle** (any previously seen generation recurs). The response includes metadata saying which occurred. | Oscillators such as the blinker never become still. Defining conclusion as stillness alone would classify them incorrectly as non-terminating. |
| Gliders, which translate indefinitely on an unbounded grid | On a finite grid with dead borders a glider eventually reaches the edge and dies out, producing a fixed point. | A direct consequence of the topology decision above, recorded here so the behaviour is not surprising. |
| How many attempts before giving up? | A configured `maxGenerations` (default 1000), overridable per request up to a server ceiling. Exceeding it returns 422. | Reaching the limit is a documented outcome, not a server fault, so it is a client-visible 4xx rather than a 500. |
| Do generation queries mutate the board? | **No.** Fetching generation N is a pure read; the stored board is never advanced by a query. | Keeps the API idempotent and safely cacheable. |
| Board id format | Server-generated UUID. Never client-supplied. | Avoids collisions and enumeration of other callers' boards. |
| Input format | JSON with `width`, `height`, and `cells` as a row-array of booleans. | A single fully specified format, readable in tests and in the example requests. |

---

## 3. Architecture

A single Spring Boot service, four packages, with the simulation engine independent of both the web framework and the persistence layer.

```
┌──────────────────────────────────────────────┐
│ web        REST controller, DTO records,     │
│            validation, error handler         │
├──────────────────────────────────────────────┤
│ service    BoardService — orchestration,     │
│            memoisation, generation limits    │
├──────────────────────────────────────────────┤
│ domain     Board, LifeEngine, StateCodec,    │
│            TerminationDetector — plain Java  │
│            plus SLF4J, no Spring or JDBC     │
├──────────────────────────────────────────────┤
│ repository BoardRepository interface +       │
│            SQLite implementation              │
└──────────────────────────────────────────────┘
                      │
                 SQLite file
```

The `domain` package has no Spring, JDBC, or Jackson imports. It may log through SLF4J, which is a facade rather than the web or persistence stack, so the rules engine is still testable without an application context. Levels are in §9.

`BoardRepository` is the only single-implementation interface in the codebase. It exists because storage is the stated extension point (§3.1), not as a reflex.

### 3.1 Persistence: SQLite

The deciding factor was what it costs to run this project. A server-backed database would mean installing it, starting a container, and configuring credentials before a single endpoint could be exercised — setup that has nothing to do with the problem and that fails on someone else's machine in ways neither of us can debug. SQLite is a file. Clone the repository, run one command, and the service is up.

That is not a compromise on the stated requirement. SQLite is ACID, with WAL mode enabled here; killing the process mid-request leaves the last committed state intact on disk, which is exactly the durability the brief asks for.

**Known limitation:** SQLite is single-writer and file-local, so this design does not scale horizontally as written. `BoardRepository` is the seam — moving to Postgres or another server-backed store means a new implementation of that interface and a configuration change, with no impact on the domain, service or web layers.

### 3.2 Data model

**`board`**

| column | type | notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `width`, `height` | INTEGER | immutable after creation |
| `initial_state` | TEXT | generation 0, flat `0`/`1` string |
| `created_at` | TEXT | ISO-8601 UTC |
| `max_generations` | INTEGER NULL | honored when set; the upload API always stores NULL |

**`generation`** — memoisation cache

| column | type | notes |
|---|---|---|
| `board_id` | TEXT FK | |
| `idx` | INTEGER | generation number |
| `state` | TEXT | flat `0`/`1` string |
| | | PK `(board_id, idx)` |

Two tables. Caching computed generations makes repeated reads O(1) after the first computation, and means work already done is not repeated after a restart.

Termination is not stored. `GET /final` always walks from generation 0 in memory. It does not read the generation table, and it does not write the generations it computes. A later `GET /generations/{n}` therefore does not reuse a `/final` walk, and calling `/final` twice walks twice. The walk is bounded by the generation cap (§1.4). The cell-generation budget applies only to `/generations/{n}`, which writes a row per step. The cache makes repeated generation reads cheap. It does not make `/final` cheap.

`max_generations` on `board` is a stored cap the service will honor when the request does not pass its own. `POST /boards` does not accept one, so the column is NULL for every board created through the API. NULL means "use the default in `application.yml` at request time". Changing that default changes `/final` for boards already stored. The column does not snapshot the default at upload.

The schema is created at startup with `CREATE TABLE IF NOT EXISTS`. A migration tool was considered and rejected: at two tables with no versioned history it would add a dependency and a build step without solving a problem this project has.

`PRAGMA journal_mode=WAL` is stored in the database file, so a new connection still sees it. `PRAGMA foreign_keys` is not. SQLite defaults it to off on every connection. Startup sets it, and the pool runs `PRAGMA foreign_keys=ON` as each connection is opened (`spring.datasource.hikari.connection-init-sql`). `busy_timeout` is also per connection. The JDBC URL sets it to 5000 ms; the driver default is 3000 ms and is not left implicit. See §8. A `sqlite3` shell is a different connection: `PRAGMA journal_mode` there shows `wal`, and `PRAGMA foreign_keys` shows `0` until that shell turns it on.

### 3.3 State encoding

`StateCodec` provides `serialize` and `deserialize` between a `boolean[][]` grid and a flat string, read row-major, with `'1'` for a live cell and `'0'` for a dead one. A 3×3 blinker in its horizontal phase is `"000111000"`.

Three properties follow from this choice:

- **The stored form is readable.** Opening the database file during debugging shows the grid directly, rather than a value that has to be decoded before it means anything.
- **The string is what `/final` compares when two hashes match** (§4.3). The map itself stores the hash, so a long walk does not keep every grid.
- **One column holds the whole grid**, so advancing a generation is one row write rather than one write per cell. The obvious alternative — a row per cell, with `row`, `column` and `alive` columns — would turn a 100×100 board into 10,000 writes per generation for no benefit, since no query ever needs to address a single cell.

Bit-packing into a byte array was considered and rejected. At the board sizes this service targets the storage saving is irrelevant, and it trades a readable column and a trivially testable codec for a class whose defects corrupt stored state silently.

A round-trip test covers `serialize`/`deserialize` before any persistence code exists, along with a length check that rejects a string whose length does not equal `width × height`.

---

## 4. Simulation engine

### 4.1 Rules

B3/S23. A live cell with two or three live neighbours survives; a dead cell with exactly three live neighbours becomes live; all other cells die or stay dead. The neighbourhood is the eight surrounding cells, and out-of-bounds positions count as dead.

### 4.2 Implementation

The live grid is a `boolean[][]` indexed `[row][column]`, the same layout `StateCodec` uses. Each step allocates the next grid and writes into it. The input is not mutated: the caller may still hold it, and generation N and N+1 both have to exist while the step runs. There is no per-cell object and no collection in the inner loop. The neighbour count is one bounds check per adjacent cell.

A flat `boolean[]` and a reused pair of buffers would avoid allocating one array per generation. That was rejected. Every other layer already thinks in rows and columns, and the cost that matters at these sizes is the neighbour loop, not the array header.

Interior and border cells use the same bounds-checked neighbour count. A faster interior path was rejected as a second implementation to keep correct, which this service does not need.

### 4.3 Termination detection

Generations are walked forward with a map from a fingerprint of the encoded state to the generation indexes where that fingerprint appeared:

- The next state equals the current state → **fixed point**, period 1. An all-dead board is reported as `EXTINCT` for clearer semantics.
- The next fingerprint is already in the map → rebuild that earlier generation and compare the full strings. Equal strings are a **cycle**, with `firstOccurrence` the stored index and `period` the difference between the current and stored indices. Unequal strings are a hash collision, and the walk continues.
- `maxGenerations` reached with neither condition met → **no conclusion**, returned as 422 with the number of generations attempted.

The fingerprint is two 64-bit FNV-1a hashes of the same bytes. They share a prime and a similar mix, so this is not an independent 128-bit key. A collision does not change the answer, because the full string is the check. The map stays small because it stores hashes and indexes, not grids. That is what lets `/final` use the generation ceiling on a 300×300 board (§1.4).

The earlier string is not kept for every generation. Replaying it from generation 0 would repeat the whole lead-in, about as much work as the walk that found the cycle. A checkpoint of the encoded state is kept every 256 generations, including generation 0. Confirming a match replays at most 255 steps from the nearest checkpoint. On a 300×300 board that is about 40 strings, roughly 3.6 MB, at the generation ceiling.

Floyd's and Brent's cycle-detection algorithms were considered. They use constant memory, but the map additionally yields the cycle entry point and period directly.

---

## 5. API

Base path `/api/v1`. JSON throughout. Errors follow RFC 7807 (`application/problem+json`).

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/boards` | Upload a board, return id | `201` + `Location` | `400` validation, malformed grid, oversized board, body too large, null cell, or bad JSON |
| `GET` | `/boards/{id}` | Metadata and generation 0 | `200` | `400` id is not a UUID, `404` |
| `GET` | `/boards/{id}/next` | One generation forward | `200` | `400` id is not a UUID, `404` |
| `GET` | `/boards/{id}/generations/{n}` | State n generations away | `200` | `404`, `400` (negative, not an integer, above the ceiling, or over the cell-generation budget) |
| `GET` | `/boards/{id}/final` | Final state | `200` with termination metadata | `400` when `maxGenerations` < 1, `404`, `422` |

- `/next` delegates to the same code path as `/generations/1`. It is exposed separately because the specification names it as a distinct capability.
- `POST`, `GET /boards/{id}`, `/next`, and `/generations/{n}` return `id`, `width`, `height`, `generation`, and `cells`. `cells` is a row-array of booleans, the same shape as the upload.
- `/final` returns `id`, `width`, `height`, `cells`, `terminationKind`, `firstOccurrenceGeneration`, `period`, `generationsComputed`, and `generationsLimit`. It does not include a `generation` field. The cells are the state at `firstOccurrenceGeneration` (the state that repeated). `generationsComputed` is how many steps the walk took, which is larger than `firstOccurrenceGeneration` when the board had a lead-in before the cycle. `generationsLimit` is the cap the walk used, after the ceiling clamp. A caller who asked for 100000 and was clamped sees `10000` here, not only in the server log.
- `maxGenerations` is an optional query parameter on `/final`. A value below 1 is `400`. A value that is not an integer is `400` with title `Bad Request`, and the detail quotes the text that was sent. A repeated parameter is the same `400` when conversion fails, and the detail lists every value (`abc, 5`) so the text does not change between requests. A value above `max-generations-ceiling` is clamped down to the ceiling and the walk uses the ceiling. The clamp is not an error. It is visible as `generationsLimit`. The cell-generation budget does not change this number.
- Limit resolution for `/final`, before those clamps: the query parameter if present, otherwise `board.max_generations` if that column is non-null, otherwise `game-of-life.max-generations`.
- Input is validated at the edge: positive dimensions (`@Min`, title `Validation failed`, with the field name), cell count matching the declared dimensions (title `Invalid board`), a null cell (400, the body is not a board of booleans), and the caps in §1.4. A generation index below 0, above the ceiling, or past the cell-generation budget is `400`.
- A path id that Java cannot parse as a UUID, or a generation index that Spring cannot parse as a number, is `400`. An id that parses and is not stored is `404`. Java's parser, and a few other framework defaults, are wider than the examples in the scripts. They are listed in §5.1.
- A missing resume row, when the board itself exists, is a server error (500). That is a broken cache, not an unknown board.
- No `GET` mutates the stored board. The only writes outside `POST /boards` go to the memoisation cache, and only from `/next` and `/generations/{n}`. `/final` does not write the cache. Cache writes are not visible as a change to generation 0.
- Resuming a generation read starts at the highest cached index and walks forward. If that index is already past the one requested, and the requested row is missing, the walk starts again from generation 0. It does not return the later row as if it were the requested generation.
- Bean validation (`width` below 1, missing `cells`) is answered by `ApiExceptionHandler`, which extends `ResponseEntityExceptionHandler` so it runs ahead of Spring Boot's problem-details handler. The title is `Validation failed`. The detail names the fields in a fixed order: `width`, then `height`, then `cells`. Any other field follows those, alphabetically. A body that is not JSON, a non-UUID id, an unknown method (405), or an unsupported media type (415) keeps Spring's own titles (`Bad Request`, `Method Not Allowed`, `Unsupported Media Type`). A query value Spring cannot convert, including a repeated `maxGenerations`, is also title `Bad Request`; the handler replaces Spring's array text with the values themselves. A 422 body adds `generationsAttempted`.

### 5.1 Inherited defaults

These are not rules of the game, and they are not checks this service added. They are what Spring Boot, Jackson, and Tomcat do because nothing in this project turns them off. Each one was reproduced against a running process.

| What a caller can do | What happens | Where it comes from |
|---|---|---|
| Send a cell as `1` or `0` instead of `true` or `false` | Stored as live or dead. `201`. | Jackson coerces those numbers to booleans. |
| `GET /boards/1-1-1-1-1` | `404`, not `400`. The id is read as `00000001-0001-0001-0001-000000000001`. | `UUID.fromString` accepts that short form. `not-a-uuid` is still `400`. |
| `GET .../generations/0x2` | Generation 2. `200`. | Spring's number conversion accepts hex. |
| Valid JSON, then extra text | The board is stored. `201`. | Jackson does not reject trailing tokens unless that check is enabled. |
| `Accept: application/xml` on an upload | The board is stored, then the response is `406`. | The controller runs before Spring looks for an XML writer. There is none. This is not the `415` for a bad `Content-Type`. |
| Connect from another machine | The port accepts it. | `application.yml` does not set `server.address`. Tomcat listens on every interface, port 8080. |
| Repeat `maxGenerations`, and make the first value an integer | `200`, using that first value. Later copies are ignored, even when they are not integers. | Spring converts a repeated query parameter from its first element. A repeated parameter whose first value is not an integer is `400`, and the detail lists every value. |

The demo scripts have a separate limit, and it is in the shell rather than the service. `scripts/common.sh` passes the upload body to curl with `-d`, so the JSON is one command-line argument. `try-large-glider.sh` (300×300, about 616 KiB) and `try-oversized.sh` (301×301, about 620 KiB) both do this. On Linux one argument cannot exceed 128 KiB, and the shell fails with "Argument list too long" before curl connects. macOS does not have that per-argument cap, which is why the scripts succeed there. The service itself will accept those bodies. Sending the body on stdin avoids the limit.

```json
{
  "type": "about:blank",
  "title": "No conclusion",
  "status": 422,
  "detail": "No conclusion reached within 1 generations",
  "generationsAttempted": 1
}
```

Upload, generation 0 of a horizontal blinker:

```json
{
  "id": "2f1c0b7e-4a0e-4f1a-9c2d-6b7e8f901234",
  "width": 3,
  "height": 3,
  "generation": 0,
  "cells": [[false, false, false], [true, true, true], [false, false, false]]
}
```

`/final` for that blinker. `generationsLimit` is the cap that was applied, here the default 1000. The walk itself stopped at generation 2.

```json
{
  "id": "2f1c0b7e-4a0e-4f1a-9c2d-6b7e8f901234",
  "width": 3,
  "height": 3,
  "cells": [[false, false, false], [true, true, true], [false, false, false]],
  "terminationKind": "CYCLE",
  "firstOccurrenceGeneration": 0,
  "period": 2,
  "generationsComputed": 2,
  "generationsLimit": 1000
}
```

DTOs are Java `record`s constructed by the controller. No code-generation or mapping libraries are used.

---

## 6. Testing

### 6.1 Coverage by layer

1. **Engine unit tests**, no application context: block (still life), blinker (period 2), toad, beacon, glider translating and then dying at the boundary, empty board, single cell, fully live board, and 1×1 and 1×N shapes.
2. **Codec tests** — randomly generated boards survive a `serialize`/`deserialize` round trip unchanged; a string whose length does not match `width × height` is rejected.
3. **Termination tests** — fixed point, cycle of period greater than 1, extinction, a board that exceeds `maxGenerations` (with a small configured limit to keep the test fast), a forced hash collision that is not treated as a cycle, and a cycle confirmed from a checkpoint.
4. **Repository tests** against a temporary-file SQLite database.
5. **API integration tests** using `@SpringBootTest` on a random port and `TestRestTemplate`, against a temporary database file, not `data/game-of-life.db`. They cover every endpoint in §5. Over HTTP they assert the blinker's cells, the 404 title, the validation title and field name, several invalid fields in the order `width`, `height`, `cells`, a missing `cells` field, a null cell, a body that is not JSON, a repeated non-numeric `maxGenerations` whose detail names both values, a body whose `Content-Length` is over the cap, a chunked body that crosses the cap inside `cells`, and `generationsAttempted` on the 422. The request-size cap in that test is 12,000 bytes, above Jackson's first 8,000-byte read, so the chunked case fails while the parser is inside the array. They do not cover every error path: a non-UUID id, `maxGenerations=0`, the generation ceiling, and an oversized board are covered by the scripts and, where noted below, by `BoardServiceTest`. Other patterns' cells are pinned in `LifeEngineTest` and `BoardServiceTest`.
6. **Restart test** — a board is created and advanced, the application context is shut down, a new context is started against the same database file, and the board and its cached generations are asserted intact. This is the direct test of the durability requirement in §1.2.

### 6.2 Traceability

| Requirement | Covered by |
|---|---|
| F1 Upload board | `BoardApiTest#createBoardReturnsIdAndLocation` |
| GET board | `BoardApiTest#getReturnsUploadedBoard` |
| F2 Next state | `BoardApiTest#nextReturnsFollowingGeneration`, `BoardApiTest#nextIsIdempotent`, `LifeEngineTest#blinkerOscillates`, `BoardServiceTest#readsAreIdempotent` |
| F3 N generations away | `BoardApiTest#generationsAtIndexReturnsExpectedState`, `BoardServiceTest#resumesFromCache` |
| F3 Index past the ceiling | `BoardServiceTest#rejectsANegativeIndexAndAnIndexPastTheCeiling` |
| F4 Final state | `BoardApiTest#finalReturnsTerminationMetadata`, `TerminationDetectorTest#detectsCycle`, `BoardServiceTest#usesTheConfiguredDefaultAndReturnsAConclusion` |
| F4 Cycle entry point | `TerminationDetectorTest#reportsCycleEntryPoint`, `TerminationDetectorTest#cycleConfirmedFromACheckpoint` |
| F4 Hash collision is not a cycle | `TerminationDetectorTest#hashCollisionDoesNotInventACycle` |
| F4 No conclusion | `BoardApiTest#finalReturns422WhenLimitExceeded`, `TerminationDetectorTest#noConclusionWithinLimit`, `ApiExceptionHandlerTest#noConclusionIs422AndReportsHowFarTheWalkGot` |
| F4 Caller limit clamped | `BoardServiceTest#clampsRequestedLimit` |
| F4 Cell-generation budget on `/generations`; `/final` uses the generation cap | `BoardServiceTest#rejectsAWalkPastTheCellGenerationBudget`, `BoardServiceTest#finalStateIgnoresTheCellGenerationBudget` |
| F4 Non-positive caller limit | `BoardServiceTest#rejectsANonPositiveRequestedLimit` |
| F4 Stored per-board cap | `BoardServiceTest#usesTheBoardLimitWhenTheCallerDoesNotSupplyOne` |
| Unknown id | `BoardApiTest#unknownBoardReturns404` |
| Malformed upload | `BoardApiTest#malformedUploadReturns400`, `BoardServiceTest#rejectsDimensionMismatch` |
| Null cell | `BoardApiTest#nullCellReturns400` |
| Oversized board | `BoardServiceTest#rejectsOversizedBoard` |
| Request body over the byte cap | `BoardApiTest#oversizedBodyReturns400`, `BoardApiTest#chunkedOverflowInsideCellsIsRequestTooLarge` |
| Broken generation cache | `BoardServiceTest#missingBoardAndMissingResumePoint` |
| Durability | `RestartPersistenceTest#stateSurvivesContextRestart` |
| Rules correctness | `LifeEngineTest` (block, blinker, toad, beacon, glider, empty, single cell, full board, 1×1, 1×N) |
| Encoding integrity | `StateCodecTest#roundTripPreservesBoard` |
| Cache survives restart | `RestartPersistenceTest#stateSurvivesContextRestart` |

A JaCoCo report is produced on `mvn test` (`target/site/jacoco/index.html`). The last run measured about 99% instruction coverage on `domain` and about 98% on `service`. The build does not fail below a threshold. Configuration validation branches and DTO accessors are not part of that target.

### 6.3 Running the examples

`./try-all.sh` runs every HTTP scenario against a live process. `try-it.sh` is the blinker alone, and it checks status and cells, not only that curl returned. `scripts/try-*.sh` is one file per other board and error case: still life, toad, beacon, glider, a 300×300 glider, plus-sign lead-in, empty, single cell, full board, 1×1, 1×N, unknown id, `width` 0, a mismatched grid, a null cell, missing `cells`, bad JSON, an oversized board, a negative generation, a generation above the ceiling, a generation past the cell-generation budget, and a bad or clamped `maxGenerations`. The 300×300 glider is the natural no-conclusion: `/generations/56` is 400, the default `/final` is 422 after 1,000 generations, and `maxGenerations=10000` reaches the corner still life at generation 1,192. The clamped call asserts `generationsLimit`, not the server log. `requests.http` is the blinker walk, one request at a time. No OpenAPI tooling is included. The two large-board scripts pass the body on curl's command line and fail on Linux with "Argument list too long" (§5.1). They succeed on macOS.

Restart is two steps, because the process has to stop in between: `./try-restart.sh save`, stop or kill the server, start it, `./try-restart.sh check`. The automated equivalent is `RestartPersistenceTest`.

---

## 7. Possible extensions

- Optional per-board generation cap on upload. `Board.maxGenerations` and the nullable `max_generations` column already exist, and `/final` already prefers that column when the caller omits `maxGenerations`. The upload body does not accept it yet, so boards created through the API store null and use the server default.
- Infinite or toroidal topology as a per-board option.
- RLE pattern import for standard Game of Life pattern files.
- A sparse representation for large, mostly-dead grids, and HashLife for very deep generation counts.
- A server-backed datastore behind the existing `BoardRepository` interface, for horizontal scale.
- Rate limiting and request quotas.

## 8. Concurrency note

Two concurrent requests may compute the same uncached generation simultaneously. The resulting write race is benign: generation rows are immutable for a given `(board_id, idx)`, and inserts use `INSERT OR IGNORE`, so either writer produces the same row. `/final` does not take that path. It does not write generation rows.

SQLite allows one writer at a time. Readers proceed concurrently because the file is in WAL mode. Hikari's default pool is 10 connections, and more than one of them can be in a write. If a write holds the lock longer than `busy_timeout` (5000 ms, set on the JDBC URL; the driver default is 3000 ms), the waiting writer gets `SQLITE_BUSY`, which surfaces as a 500. The cell-generation budget in §1.4 is what keeps a single write from running that long on the boards this service accepts. The timeout is explicit so that wait is a choice, not the driver's hidden default.

## 9. Logging

Default level for `life.simulation.engine` is INFO. Grids are not logged. A board can be 90,000 cells, and the stored `0`/`1` string is already visible in SQLite.

| Level | What is logged |
|---|---|
| INFO | Process start, the limits from configuration, schema ready, each HTTP call, a board saved, and the outcome of `/final` (kind, period, generations). |
| WARN | 400 and 404, and a caller `maxGenerations` clamped to the ceiling. Also a generation cache that has a later row but not the one requested. |
| DEBUG | Method entry with ids, sizes, and indexes. Cache hit, cache miss, and the index a walk resumes from. Not the grid. |
| TRACE | One line per cell inside the neighbour count. DEBUG does not enable this. Turn it on only for `LifeEngine`, and only on a small board. |

A 422 is INFO. Reaching the generation cap is a documented result, not a fault.

Unexpected failures are left to Spring. They log at ERROR with a stack trace. The handlers above do not log a stack for a 400, 404, or 422.
