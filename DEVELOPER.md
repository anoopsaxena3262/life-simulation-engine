# Developer guide

Onboarding for engineers maintaining this service. Product behaviour and the reasons behind it live in [DESIGN.md](DESIGN.md). Day-to-day commands live here.

The service is a Spring Boot REST API for Conway's Game of Life. Board state is stored in a SQLite file. Domain code (the rules, the codec, termination) has no Spring or database dependencies. Persistence, orchestration, and HTTP sit in separate packages.

## Local setup

You need two tools:


| Tool  | Required version              | Check           |
| ----- | ----------------------------- | --------------- |
| JDK   | 25 (the release in `pom.xml`). JDK 21 will not build this project. | `java -version` |
| Maven | 3.9 or newer                  | `mvn -version`  |


Maven prints the JDK it is actually using. That line must be a Java 25 runtime. A newer JDK on your `PATH` will be picked up instead, and the build is not what this repo declares.

Point Maven at a JDK 25 before any Maven command. On macOS with Homebrew's `openjdk@25`:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

The `sqlite3` shell is optional. You only need it to inspect the database by hand. macOS includes it. Confirm with `sqlite3 -version`.

Clone the repository and work from the project root (the directory that contains `pom.xml`). The database path in configuration is relative to the process working directory. Starting the app from another directory creates a different file.

The first Maven command downloads dependencies and needs network access:

```bash
mvn -B test
```

That compiles `src/main` and `src/test` and runs the suite. Expected result: `BUILD SUCCESS`. A failure names the layer. See [Tests](#tests).

### Layout


| Path                                              | What it is                                                                 |
| ------------------------------------------------- | -------------------------------------------------------------------------- |
| `src/main/java/life/simulation/engine/domain`     | Rules, encoding, termination. No framework imports.                        |
| `src/main/java/life/simulation/engine/repository` | `BoardRepository` and the SQLite implementation.                           |
| `src/main/java/life/simulation/engine/service`    | Validation, cache, generation limits.                                      |
| `src/main/java/life/simulation/engine/web`        | Controller, request and response records, error handler.                   |
| `src/main/resources/application.yml`              | Port default, datasource URL, generation and cell caps.                    |
| `src/test/java`                                   | One test class per layer. Names match the traceability table in DESIGN.md. |
| `try-it.sh`                                       | Happy path. Blinker: next, generation 10, final, and the 422.              |
| `try-all.sh`                                      | Runs every demo script, in order. Stops at the first failure.              |
| `try-restart.sh`                                  | `save`, stop the server, start it, `check`. The board is still there.      |
| `restart.sh`                                      | Asks whether to keep or delete `data/game-of-life.db`, then starts the app. |
| `scripts/try-*.sh`                                | One other scenario each. Same running service. See [Scripts](#scripts).    |
| `requests.http`                                   | The same calls, one at a time, if the editor can send HTTP files.          |
| `data/game-of-life.db`                            | Created on first start. Not source. Do not commit it.                      |


`application.yml` does not set `server.port`, so the process binds to port **8080**.

## Start and stop

Two terminals. The first one stays on the server. The second one is where you call the API. Do not type `curl` into the terminal that is printing logs.

### Start

From the project root, with `JAVA_HOME` set as above:

```bash
mvn spring-boot:run
```

Leave this terminal running. The service is up when the log contains `Started GameOfLifeApplication`. It is listening at `http://localhost:8080`. The first start also logs `schema initialised successfully` and creates `data/game-of-life.db`.

If startup says "address already in use", an earlier instance is still on port 8080. Stop that one first (Ctrl+C in its terminal, or `kill` the PID from `lsof -nP -iTCP:8080 -sTCP:LISTEN`).

### Try it

Open a second terminal, still in the project root, and run:

```bash
./try-it.sh
```

The script uploads a 3×3 blinker, reads the id from the `Location` header, and then calls `/next` twice, generation 10, `/final`, and `/final?maxGenerations=1`. You do not copy the id yourself.

What the output should show:

- Upload status `201` and the id.
- Both `/next` calls with the same JSON, `"generation": 1`, middle column live.
- Generation 10 matching the uploaded board (a blinker at an even index is back to generation 0).
- `/final` with `"terminationKind": "CYCLE"` and `"period": 2`.
- The last call with `HTTP 422`.

The first terminal prints one INFO line per call while the script runs. That is the server log, not a second prompt.

If the script says nothing is listening, the service in the first terminal has not reached `Started GameOfLifeApplication` yet. The other boards and the error calls are in [Scripts](#scripts).

### Stop

Go back to the first terminal and press **Ctrl+C**. Wait until the process exits. The database file stays on disk, so the next start still has the boards.

`mvn test` does not need this process. It starts its own short-lived server on a temporary database file, not `data/game-of-life.db`. Stop the one on 8080 before you run the suite if you want the log to stay readable. The tests use a random port, so they do not require 8080 to be free.

## Scripts

These hit a service that is already running on port 8080. They are the demo. They are not a substitute for `mvn test`.

To run every scenario in one go:

```bash
./try-all.sh
```

That runs the happy path and then each file below, in that order. It prints `PASS` after each one and stops at the first failure. Run a single script when you want to show one feature.

Each script reads the id from the upload itself. It prints the JSON and exits non-zero if the status, the cells, or the termination fields are wrong. `try-it.sh` is the happy path (a blinker). Everything else is one file under `scripts/`, with its own board or its own bad request. `scripts/common.sh` is the shared curl helper. Do not run that file on its own.

| Script | Feature | What you should see |
|---|---|---|
| `./try-it.sh` | Upload, next, N generations, final, no conclusion | `CYCLE`, period 2, and `HTTP 422` when the limit is 1. The natural 422 is `try-large-glider.sh` |
| `./scripts/try-fixed-point.sh` | Still life | `/next` matches the upload. `FIXED_POINT`, period 1 |
| `./scripts/try-toad.sh` | Period-2 oscillator | `CYCLE`, period 2. Generation 2 matches the upload |
| `./scripts/try-beacon.sh` | Period-2 oscillator | `CYCLE`, period 2. Generation 2 matches the upload |
| `./scripts/try-glider.sh` | Finite board, dead borders | Generation 4 has moved. `/final` is `FIXED_POINT` |
| `./scripts/try-large-glider.sh` | Cell-generation budget, and a glider that outlives the default walk | `/generations/56` is `HTTP 400`. Default `/final` is `HTTP 422` with `generationsAttempted` 1000. `maxGenerations=10000` is `FIXED_POINT` at generation 1192, `generationsLimit` 10000 |
| `./scripts/try-empty.sh` | Extinction | `EXTINCT` |
| `./scripts/try-single-cell.sh` | Underpopulation | `/next` is all dead. `EXTINCT` |
| `./scripts/try-full-board.sh` | Collapse to extinction | `/next` is the four corners. `EXTINCT` |
| `./scripts/try-one-by-one.sh` | 1×1 grids | Both end `EXTINCT`. The live cell's `/next` is dead |
| `./scripts/try-one-by-n.sh` | 1×N grids | `/next` kills the cells at each end |
| `./scripts/try-not-found.sh` | Unknown id | `HTTP 404`, title `Board not found` |
| `./scripts/try-invalid-board.sh` | Malformed upload | `"width": 0` returns `HTTP 400`, title `Validation failed` |
| `./scripts/try-mismatched-board.sh` | Cells do not match the declared size | `HTTP 400`, title `Invalid board` |
| `./scripts/try-bad-generation.sh` | Generation index below 0 | `HTTP 400` |
| `./scripts/try-bad-limit.sh` | Non-positive `maxGenerations` | `HTTP 400`, not 422 |
| `./scripts/try-bad-id.sh` | Path id is not a UUID | `HTTP 400` |
| `./scripts/try-limit-clamped.sh` | Caller limit above the ceiling | Still `CYCLE`. `generationsLimit` is 10000 |
| `./scripts/try-plus.sh` | Cycle with a lead-in | `firstOccurrenceGeneration` 4, `generationsComputed` 6 |
| `./scripts/try-oversized.sh` | Board over `max-cells` | `HTTP 400`, title `Invalid board` |
| `./scripts/try-generation-ceiling.sh` | `/generations/10001` | `HTTP 400` |
| `./scripts/try-bad-json.sh` | Body is not JSON | `HTTP 400`, title `Bad Request` |
| `./scripts/try-missing-cells.sh` | No `cells` field | `HTTP 400`, title `Validation failed` |
| `./scripts/try-null-cell.sh` | A null cell | `HTTP 400`. Not stored as dead |

`./try-all.sh` does not stop the server. Restart and crash are a separate two-step demo, because the process has to die in between.

```bash
./try-restart.sh save
```

That uploads a blinker and asks for generation 10, so the file holds the board and generations 0 through 10. Then stop the service. Ctrl+C is a normal shutdown. `kill -9` of the process on port 8080 is the crash. Start it again with `mvn spring-boot:run`, then:

```bash
./try-restart.sh check
```

The same id returns the uploaded blinker. If `sqlite3` is installed, the script also reads `data/game-of-life.db` and requires indexes 0 through 10 to still be there. It does that before any call that would recompute a missing generation. The id is stored in `data/restart-demo.id`, which is not source.

`mvn -Dtest=RestartPersistenceTest test` is the same proof with no manual stop. It uses its own database file, so it does not need the server on 8080 to be down.

## Tests

### What the suite covers

`mvn test` is the completeness check. It does not use the server you started with `mvn spring-boot:run`. The same behaviours the scripts demonstrate are pinned here, plus the database and the restart.

| Feature | Script, if you want to see it | Test |
|---|---|---|
| Upload, return an id | `./try-it.sh` | `BoardApiTest#createBoardReturnsIdAndLocation` |
| Next generation, and calling it twice | `./try-it.sh` | `BoardApiTest#nextReturnsFollowingGeneration`, `BoardApiTest#nextIsIdempotent`, `LifeEngineTest#blinkerOscillates` |
| State N generations away | `./try-it.sh`, `./scripts/try-toad.sh` | `BoardApiTest#generationsAtIndexReturnsExpectedState`, `BoardServiceTest#resumesFromCache` |
| Final state: cycle, fixed point, extinct | the board scripts | `BoardApiTest#finalReturnsTerminationMetadata`, `TerminationDetectorTest`, `LifeEngineTest` |
| No conclusion within the limit | `./try-it.sh` | `BoardApiTest#finalReturns422WhenLimitExceeded` |
| Limit clamped to the ceiling | `./scripts/try-limit-clamped.sh` | `BoardServiceTest#clampsRequestedLimit` |
| `maxGenerations` below 1 | `./scripts/try-bad-limit.sh` | `BoardServiceTest#rejectsANonPositiveRequestedLimit` |
| Unknown id, malformed board, bad index | the `try-not-found`, `try-invalid-board`, `try-bad-generation` scripts | `BoardApiTest`, `ApiExceptionHandlerTest` |
| Rules: block, blinker, toad, beacon, glider, empty, single cell, full board, 1×1, 1×N | the matching `scripts/try-*.sh` | `LifeEngineTest` |
| Encoding round trip | none (no HTTP shape for this) | `StateCodecTest` |
| Rows survive a restart or a kill | `./try-restart.sh save`, then `check` | `RestartPersistenceTest#stateSurvivesContextRestart` |

### Automated

Run everything from the project root, with `JAVA_HOME` pointed at JDK 25:

```bash
mvn test
```

Run one class, or one method, when a failure is in a single layer:

```bash
mvn -Dtest=StateCodecTest test
mvn -Dtest=LifeEngineTest#blockIsStable test
mvn -Dtest=BoardApiTest#finalReturns422WhenLimitExceeded test
```

JaCoCo writes a report after a successful test run:

```bash
open target/site/jacoco/index.html
```

DESIGN.md records the measured JaCoCo numbers. The build writes the report and does not fail below a threshold. Configuration and DTO accessors are not part of that target.

The suite is layered. A failure should be read in this order, because a red HTTP test can be a rules bug, a SQL bug, or an HTTP bug, and the lower layer tells you which:

1. `StateCodecTest`, `LifeEngineTest`, `TerminationDetectorTest` — no Spring context. These pin the `0`/`1` encoding, B3/S23, and fixed-point / cycle / extinction / give-up, including a forced hash collision and a cycle confirmed from a checkpoint.
2. `BoardRepositoryTest` — SQLite in a temporary file. Save, read, idempotent generation insert, highest cached index.
3. `BoardServiceTest` — validation, cache hit, resume, limit clamp, non-positive `maxGenerations`. The repository is a mock except where the test builds a small in-memory cache.
4. `ApiExceptionHandlerTest` — status, title, and the `generationsAttempted` field on a 422. No web server.
5. `BoardApiTest` — full Spring context on a random port, with its own temporary database file. Every path in DESIGN.md section 5, plus 404, 400, and 422. These tests check the blinker's cells, the validation title, a null cell, a declared body over the request-size cap, a chunked body that crosses that cap inside `cells`, and `generationsAttempted` on the 422. Other patterns stay in `LifeEngineTest`.
6. `RestartPersistenceTest` — start a context, write a board and generations 0 through 4, shut it down, start a new context on the same file, read the rows back. It does not call `generationAt` on the second context, because that method would recompute a missing cache and hide a lost write.

`BoardApiTest` and `RestartPersistenceTest` boot the application. They are the wrong place to learn a single rule or a single SQL statement.

### By hand, against a running service

Follow [Start and stop](#start-and-stop) and the [Scripts](#scripts) section. `./try-it.sh` is the blinker. The table below is what that script's responses mean.

What those calls return:


| Call                             | Status         | What to look at                                                                            |
| -------------------------------- | -------------- | ------------------------------------------------------------------------------------------ |
| `POST /boards`                   | 201            | `Location: /api/v1/boards/<id>`. Body is generation 0.                                     |
| `GET /boards/<id>`               | 200            | Same cells as the upload. `generation` is 0.                                               |
| `GET .../next` twice             | 200 both times | Same body both times. `generation` is 1. For the blinker above, the middle column is live. |
| `GET .../generations/10`         | 200            | A blinker at an even index matches generation 0.                                           |
| `GET .../final`                  | 200            | `terminationKind` is `CYCLE`, `period` is 2.                                               |
| `GET .../final?maxGenerations=1` | 422            | Problem title `No conclusion`, and `generationsAttempted` is 1.                            |
| `GET .../final?maxGenerations=0` | 400            | `maxGenerations` has to be at least 1. A value above the ceiling is clamped, not rejected. |
| Unknown UUID                     | 404            | Title `Board not found`.                                                                   |
| `"width": 0`                     | 400            | Title `Validation failed`, and the detail names `width`. A grid whose rows do not match the declared size is title `Invalid board`. A body that is not JSON is title `Bad Request`. |


No GET changes the stored board. Calling `/next` twice returns generation 1 both times. `/final` does not fill the generation cache. After a `/final`, generation rows exist only for indexes something has already requested through `/next` or `/generations/{n}`, plus generation 0 from the upload.

To see the lines from one of these calls, leave the level at INFO. To see cache hits and the index a walk resumed from, see [Logging](#logging).

## SQLite

The application does not use a separate database server. JDBC opens a file.

```yaml
spring.datasource.url: jdbc:sqlite:data/game-of-life.db?busy_timeout=5000
```

`busy_timeout` is 5000 ms on that URL. The driver default is 3000 ms, and it is not left implicit. A writer that waits longer than that gets `SQLITE_BUSY`, which surfaces as a 500.

The file appears the first time a process opens that URL, including `mvn spring-boot:run`. `BoardApiTest` and `RestartPersistenceTest` do not open it. Each uses its own temporary file. Inspect `data/game-of-life.db` only after you have started the app from the project root. If you start the app elsewhere, look for `game-of-life.db` next to that other working directory.

Open the shell from the project root:

```bash
sqlite3 data/game-of-life.db
```

Useful commands inside the prompt:

```sql
.tables
.schema
.headers on
.mode column
```

`.tables` lists names. `.schema` prints the `CREATE` statements. Leave with `.quit`.

After the first start the schema is two tables:

`board` — one row per upload.


| Column            | Meaning                                                                                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | UUID, stored as text. Same value the API returns.                                                                                                       |
| `width`, `height` | Fixed at upload.                                                                                                                                        |
| `initial_state`   | Generation 0 as a flat `0`/`1` string, row by row.                                                                                                      |
| `created_at`      | ISO-8601 UTC text.                                                                                                                                      |
| `max_generations` | Optional stored cap. The upload API leaves this NULL. NULL means `/final` uses the query parameter or the default in `application.yml` at request time. |


`generation` — memoised states. Primary key is `(board_id, idx)`.


| Column     | Meaning                                   |
| ---------- | ----------------------------------------- |
| `board_id` | FK to `board.id`.                         |
| `idx`      | Generation number. `0` is the upload.     |
| `state`    | Same `0`/`1` encoding as `initial_state`. |


A 3×3 horizontal blinker is the string `000111000`.

Queries that match what `SqliteBoardRepository` is written to do:

```sql
SELECT id, width, height, initial_state, created_at, max_generations
FROM board;

SELECT id, width, height, initial_state, created_at, max_generations
FROM board
WHERE id = 'paste-the-uuid-here';

SELECT idx, state
FROM generation
WHERE board_id = 'paste-the-uuid-here'
ORDER BY idx;

SELECT state
FROM generation
WHERE board_id = 'paste-the-uuid-here'
  AND idx = 1;

SELECT MAX(idx)
FROM generation
WHERE board_id = 'paste-the-uuid-here';
```

`MAX` over no rows is `NULL`. The repository treats that null as "nothing cached", not as zero.

A board and every cached generation:

```sql
SELECT b.id, b.width, b.height, g.idx, g.state
FROM board b
LEFT JOIN generation g ON g.board_id = b.id
ORDER BY b.created_at, g.idx;
```

Check write-ahead logging from the shell. That setting is stored in the database file:

```sql
PRAGMA journal_mode;
```

After a correct startup that reports `wal`.

`PRAGMA foreign_keys` in this shell will report `0`. That is expected. Foreign keys are off by default on every new SQLite connection, and the `sqlite3` shell is not the application's connection. The pool turns them on as it opens each connection (`connection-init-sql` in `application.yml`). Setting the pragma once at startup would not cover the next connection the pool hands out.

Do not edit rows by hand to "fix" a board while the service is running. Generation rows are immutable for a given board and index. The application expects two writers of the same pair to produce the same state, and it inserts with `INSERT OR IGNORE` so the second writer is a no-op.

To wipe local data, stop the service, then delete the file:

```bash
rm -f data/game-of-life.db data/game-of-life.db-wal data/game-of-life.db-shm
```

The `-wal` and `-shm` files exist only while write-ahead logging is on. The next start creates a new database.

## Logging

The running default is INFO for `life.simulation.engine`. That is enough to see a request come in and to see how `/final` concluded. Grids are not written to the log. Open the database if you need the `0`/`1` string.


| Level | Turn it on when                                                                                                                                                 |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| INFO  | You want the request line, the saved board id, and the `/final` outcome (kind, period, generations). A 422 is INFO. It is a normal result.                      |
| WARN  | You already get these at the default: a 400, a 404, a caller limit that was clamped to the ceiling, or a generation cache that skipped the index you asked for. |
| DEBUG | A read returned the wrong generation and you need the cache hit, the cache miss, and the index the walk resumed from.                                           |
| TRACE | You are debugging neighbour counts. Set it only on `LifeEngine`, and only for a small board. One line per cell.                                                 |


DEBUG for one run:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.life.simulation.engine=DEBUG
```

Neighbour counts, without burying them in a DEBUG line for every other class:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.life.simulation.engine.domain.LifeEngine=TRACE
```

Do not set the whole package to TRACE. `countLiveNeighbours` runs once per cell per generation.

## Where to look next

Read DESIGN.md section 3 for the package boundaries, section 5 for the HTTP contract, section 6 for which test covers which requirement, and section 9 for the same logging levels. Change the domain package when the cells are wrong. Change `BoardService` when a cache or a limit is wrong. Change the controller or `ApiExceptionHandler` when the status or the JSON shape is wrong.