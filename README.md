# Conway's Game of Life API

A REST API implementing Conway's Game of Life, with board state persisted across restarts.

See [DESIGN.md](DESIGN.md) for the architecture, the decisions behind it, and the
requirements traceability table. Local setup, running, tests, and the SQLite
database are in [DEVELOPER.md](DEVELOPER.md).

## Running

Requires JDK 25 and Maven. JDK 21 will not build this project. From the project root:

```
mvn spring-boot:run
```

Leave that terminal open. The service is ready when the log says `Started GameOfLifeApplication`. It listens on `http://localhost:8080`. A SQLite file is created at `data/game-of-life.db` on first start.

Open a **second** terminal. One feature:

```
./try-it.sh
```

Every feature, in order, stopping at the first failure:

```
./try-all.sh
```

`./try-it.sh` uploads a blinker and prints `/next` (twice), generation 10, `/final`, and the 422. `./try-all.sh` runs that and then every script in the table below. Stop the service with Ctrl+C in the first terminal.

## Scripts

Scripts call a service that is already running. Each one checks the status, and where it matters the cells or the termination fields, and exits non-zero if that check fails. `./try-all.sh` runs the whole list. `./try-it.sh` is the happy path on its own. The others live in `scripts/`.

| Script | Feature |
|---|---|
| `./try-it.sh` | Upload, next (twice), generation 10, final `CYCLE`, then 422 |
| `./scripts/try-fixed-point.sh` | Still life. `FIXED_POINT` |
| `./scripts/try-toad.sh` | Toad. `CYCLE`, period 2 |
| `./scripts/try-beacon.sh` | Beacon. `CYCLE`, period 2 |
| `./scripts/try-glider.sh` | Glider moves, then dies at the edge. `FIXED_POINT` |
| `./scripts/try-large-glider.sh` | 300×300 glider. `/generations/56` is 400. Default `/final` is 422. The ceiling walk concludes |
| `./scripts/try-empty.sh` | Empty board. `EXTINCT` |
| `./scripts/try-single-cell.sh` | One live cell dies. `EXTINCT` |
| `./scripts/try-full-board.sh` | Full board collapses. `EXTINCT` |
| `./scripts/try-one-by-one.sh` | 1×1 dead and 1×1 live. `EXTINCT` |
| `./scripts/try-one-by-n.sh` | One row and one column. End cells die |
| `./scripts/try-not-found.sh` | Unknown id. 404 |
| `./scripts/try-invalid-board.sh` | `"width": 0`. 400 |
| `./scripts/try-mismatched-board.sh` | Cells do not match the declared size. 400 |
| `./scripts/try-bad-generation.sh` | `/generations/-1`. 400 |
| `./scripts/try-bad-limit.sh` | `maxGenerations=0`. 400 |
| `./scripts/try-bad-id.sh` | Id is not a UUID. 400 |
| `./scripts/try-limit-clamped.sh` | `maxGenerations=100000` is clamped. Response `generationsLimit` is 10000. Still `CYCLE` |
| `./scripts/try-plus.sh` | Plus sign. Cycle starts at generation 4 |
| `./scripts/try-oversized.sh` | Board over the cell cap. 400 |
| `./scripts/try-generation-ceiling.sh` | `/generations/10001`. 400 |
| `./scripts/try-bad-json.sh` | Body is not JSON. 400 |
| `./scripts/try-missing-cells.sh` | No `cells` field. 400 |
| `./scripts/try-null-cell.sh` | A null cell is rejected. 400 |

`./restart.sh` asks whether to keep or delete `data/game-of-life.db`, then starts the service. `./try-restart.sh` is the separate check that a board is still there after a stop.

How to read a failure, and which JUnit method matches each script, is in [DEVELOPER.md](DEVELOPER.md#scripts).

## Tests

`mvn test` does not use the server from `mvn spring-boot:run`, and it does not write `data/game-of-life.db`. `BoardApiTest` and `RestartPersistenceTest` each use a temporary file. It is the check that the features above, plus persistence and restart, stay correct.

From the project root, with `JAVA_HOME` on JDK 25:

```
mvn test
```

One class, or one method:

```
mvn -Dtest=LifeEngineTest test
mvn -Dtest=BoardApiTest#finalReturns422WhenLimitExceeded test
mvn -Dtest=RestartPersistenceTest test
```

| Feature | Test |
|---|---|
| Rules (block, blinker, toad, beacon, glider, empty, single cell, full board, 1×1, 1×N) | `LifeEngineTest` |
| Fixed point, cycle, extinction, give-up | `TerminationDetectorTest` |
| Encode and decode a board | `StateCodecTest` |
| Save and read rows in SQLite | `BoardRepositoryTest` |
| Validation, cache, generation limits | `BoardServiceTest` |
| 400, 404, and 422 response bodies | `ApiExceptionHandlerTest`, `BoardApiTest` |
| Every HTTP path | `BoardApiTest` |
| Board and cached generations survive a restart | `RestartPersistenceTest` |

A JaCoCo coverage report is written to `target/site/jacoco/index.html`. The layer order and the method names are in [DEVELOPER.md](DEVELOPER.md#tests).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/boards` | Upload a board, returns its id |
| `GET` | `/api/v1/boards/{id}` | Board metadata and generation 0 |
| `GET` | `/api/v1/boards/{id}/next` | One generation forward |
| `GET` | `/api/v1/boards/{id}/generations/{n}` | The state n generations away |
| `GET` | `/api/v1/boards/{id}/final` | Final state and how the board concluded |

`requests.http` walks the full happy path end to end.

## Restart, completeness, authentication

The service keeps boards across a restart or a crash. There is no login. Completeness is the test suite plus the scripts.

**Restart or crash.** With the service running:

```
./try-restart.sh save
```

Stop that process with Ctrl+C, or `kill -9` the process on port 8080. Start `mvn spring-boot:run` again, then:

```
./try-restart.sh check
```

The same id still returns the board that was uploaded. `mvn -Dtest=RestartPersistenceTest test` does this without a manual stop.

**Completeness.** `./try-all.sh` runs every HTTP scenario and stops on the first failure. `mvn test` runs the full suite, including the rules, SQLite, and the restart. DESIGN.md section 6 names the test for each behaviour.

**Authentication.** No endpoint asks for a token. None is implemented.
