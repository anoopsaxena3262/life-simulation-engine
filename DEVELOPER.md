# Developer guide

Onboarding for engineers maintaining this service. Product behaviour and the reasons behind it live in [DESIGN.md](DESIGN.md). Day-to-day commands live here.

The service is a Spring Boot REST API for Conway's Game of Life. Board state is stored in a SQLite file. Domain code (the rules, the codec, termination) has no Spring or database dependencies. Persistence, orchestration, and HTTP sit in separate packages.

## Current state of the code

The project compiles, the Spring context starts, and `mvn test` passes. Most method bodies are still stubs. They throw `UnsupportedOperationException` or, in `SchemaInitializer`, only log that schema setup is not implemented. A green test run means the harness ran. It does not mean the Game of Life behaviour is finished. Test method names and the comments inside them describe the behaviour those tests are meant to pin down.

Until `SchemaInitializer` and `SqliteBoardRepository` are implemented, starting the service creates an empty database file and the HTTP endpoints do not persist boards.

## Local setup

You need two tools:

| Tool | Required version | Check |
|---|---|---|
| JDK | 25 (the release in `pom.xml`) | `java -version` |
| Maven | 3.9 or newer | `mvn -version` |

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

That compiles `src/main` and `src/test` and runs the suite. Expected result today: `BUILD SUCCESS`, with every test passing because the test bodies are still empty.

### Layout

| Path | What it is |
|---|---|
| `src/main/java/com/example/gameoflife/domain` | Rules, encoding, termination. No framework imports. |
| `src/main/java/com/example/gameoflife/repository` | `BoardRepository` and the SQLite implementation. |
| `src/main/java/com/example/gameoflife/service` | Validation, cache, generation limits. |
| `src/main/java/com/example/gameoflife/web` | Controller, request and response records, error handler. |
| `src/main/resources/application.yml` | Port default, datasource URL, generation and cell caps. |
| `src/test/java` | One test class per layer. Names match the traceability table in DESIGN.md. |
| `requests.http` | Manual happy-path calls against a running service. |
| `data/game-of-life.db` | Created on first start. Not source. Do not commit it. |

`application.yml` does not set `server.port`, so the process binds to port **8080**.

## Start and stop

Start from the project root, with `JAVA_HOME` set as above:

```bash
mvn spring-boot:run
```

Wait until the log contains `Started GameOfLifeApplication`. The service is then at `http://localhost:8080`.

On that first start, SQLite creates `data/game-of-life.db` if the file is missing. Today the startup log also contains `schema initialisation not yet implemented`, which means the file has no tables yet.

Stop a foreground process with **Ctrl+C** in the same terminal. Spring shuts down on that signal. The database file stays on disk. That is what a later restart is supposed to read.

If the terminal is gone and the port is still taken:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Stop the listed PID with `kill <pid>`. Use `kill -9` only if a normal `kill` does not exit.

Run the packaged jar when you want the same process a deployment would start:

```bash
mvn -B -DskipTests package
java -jar target/life-simulation-engine-1.0.0.jar
```

Stop that process the same way: Ctrl+C, or `kill` of whatever is listening on 8080.

Only one process can bind to 8080. If startup fails with "address already in use", an earlier instance is still running.

## Tests

### Automated

Run everything:

```bash
mvn test
```

Run one class, or one method, while you are filling in a layer:

```bash
mvn -Dtest=StateCodecTest test
mvn -Dtest=LifeEngineTest#blockIsStable test
```

JaCoCo writes a report after a successful test run:

```bash
open target/site/jacoco/index.html
```

DESIGN.md asks for about 85% coverage on `domain` and `service`. Configuration and DTO accessors are not part of that target.

The suite is layered. Prefer this order when adding real assertions, so a failure points at one layer:

1. `StateCodecTest`, `LifeEngineTest`, `TerminationDetectorTest` — no Spring context.
2. `BoardRepositoryTest` — SQLite in a temporary file.
3. `BoardServiceTest` — service behaviour, including a mocked repository where the test says so.
4. `BoardApiTest` — full Spring context and every HTTP path in DESIGN.md section 5.
5. `RestartPersistenceTest` — start a context, write data, shut it down, start a new context on the same file.

`BoardApiTest` and `RestartPersistenceTest` boot the application. They are the wrong place to learn a single rule or a single SQL statement.

### By hand, against a running service

Start the service first. `requests.http` is the scripted walk: upload a 3×3 blinker, read it back, ask for the next generation twice (both answers must match), ask for generation 10, then ask for the final state. Replace `{id}` with the id returned by the upload.

From the shell, the same upload is:

```bash
curl -i -X POST http://localhost:8080/api/v1/boards \
  -H 'Content-Type: application/json' \
  -d '{"width":3,"height":3,"cells":[[false,false,false],[true,true,true],[false,false,false]]}'
```

Then, with the id from the `Location` header:

```bash
curl -s http://localhost:8080/api/v1/boards/<id>
curl -s http://localhost:8080/api/v1/boards/<id>/next
curl -s http://localhost:8080/api/v1/boards/<id>/generations/10
curl -s http://localhost:8080/api/v1/boards/<id>/final
curl -s 'http://localhost:8080/api/v1/boards/<id>/final?maxGenerations=50'
```

Intended outcomes, once the stubs are implemented: upload returns **201** and a `Location` of `/api/v1/boards/<id>`. Reads return **200**. An unknown id returns **404**. A malformed board returns **400**. A final-state search that exceeds the generation cap returns **422**. No GET changes the stored board. Calling `/next` twice returns generation 1 both times.

Until the controller is implemented, those calls fail inside the stub instead of returning those statuses.

## SQLite

The application does not use a separate database server. JDBC opens a file.

```yaml
spring.datasource.url: jdbc:sqlite:data/game-of-life.db
```

The file appears the first time a process opens that URL, including `mvn spring-boot:run` and `BoardApiTest`. Inspect it only after you have started the app from the project root. If you start the app elsewhere, look for `game-of-life.db` next to that other working directory.

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

There is nothing to select until `SchemaInitializer` has run. After that, the schema is two tables:

`board` — one row per upload.

| Column | Meaning |
|---|---|
| `id` | UUID, stored as text. Same value the API returns. |
| `width`, `height` | Fixed at upload. |
| `initial_state` | Generation 0 as a flat `0`/`1` string, row by row. |
| `created_at` | ISO-8601 UTC text. |
| `max_generations` | Optional per-board cap. Null means "use the default in application.yml". |

`generation` — memoised states. Primary key is `(board_id, idx)`.

| Column | Meaning |
|---|---|
| `board_id` | FK to `board.id`. |
| `idx` | Generation number. `0` is the upload. |
| `state` | Same `0`/`1` encoding as `initial_state`. |

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

Check the two pragmas the schema code is supposed to set. Foreign keys are off in SQLite unless enabled on that connection, and durability across a crash depends on write-ahead logging:

```sql
PRAGMA journal_mode;
PRAGMA foreign_keys;
```

After a correct startup those should report `wal` and `1`.

Do not edit rows by hand to "fix" a board while the service is running. Generation rows are immutable for a given board and index. The application expects two writers of the same pair to produce the same state, and it inserts with `INSERT OR IGNORE` so the second writer is a no-op.

To wipe local data, stop the service, then delete the file:

```bash
rm -f data/game-of-life.db data/game-of-life.db-wal data/game-of-life.db-shm
```

The `-wal` and `-shm` files exist only while write-ahead logging is on. The next start creates a new database.

## Where to look next

Read DESIGN.md section 3 for the package boundaries and section 6 for which test covers which requirement. The comments on each unimplemented method are the specification for that method. Implement from the domain package upward: codec, rules, termination, then the repository, then `BoardService`, then the controller and the exception handler.
