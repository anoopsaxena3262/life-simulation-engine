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

- **Durability** — the service may restart at any time and must retain board state. This drives the persistence choice, and is covered by an explicit restart test rather than asserted in prose.
- **Completeness** — every documented behaviour has a named test. See the traceability table in §6.
- **No authentication or authorisation** — out of scope for this exercise.
- Deterministic results, predictable performance on boards up to a few hundred cells per side, and clear error semantics.

### 1.3 Out of scope

Authentication, authorisation, multi-tenancy, board deletion and listing, UI, horizontal scale-out, streaming updates. Each omission is deliberate; the notes below explain where the extension points are.

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
│            TerminationDetector — plain Java, │
│            zero framework dependencies        │
├──────────────────────────────────────────────┤
│ repository BoardRepository interface +       │
│            SQLite implementation              │
└──────────────────────────────────────────────┘
                      │
                 SQLite file
```

The `domain` package has no Spring, JDBC or Jackson imports. The rules engine is therefore testable without an application context, and the simulation logic can be exercised in isolation from any transport or storage concern.

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
| `max_generations` | INTEGER NULL | per-board override |

**`generation`** — memoisation cache

| column | type | notes |
|---|---|---|
| `board_id` | TEXT FK | |
| `idx` | INTEGER | generation number |
| `state` | TEXT | flat `0`/`1` string |
| | | PK `(board_id, idx)` |

Two tables. Caching computed generations makes repeated reads O(1) after the first computation, and means work already done is not repeated after a restart.

Termination results are recomputed on demand rather than stored. Once generations are cached this is inexpensive, and it removes a third table along with its write path.

The schema is created at startup with `CREATE TABLE IF NOT EXISTS`. A migration tool was considered and rejected: at two tables with no versioned history it would add a dependency and a build step without solving a problem this project has.

### 3.3 State encoding

`StateCodec` provides `serialize` and `deserialize` between a `boolean[][]` grid and a flat string, read row-major, with `'1'` for a live cell and `'0'` for a dead one. A 3×3 blinker in its horizontal phase is `"000111000"`.

Three properties follow from this choice:

- **The stored form is readable.** Opening the database file during debugging shows the grid directly, rather than a value that has to be decoded before it means anything.
- **The string is also the cycle-detection key** (§4.3), so no separate hash is computed and there is no collision case to reason about.
- **One column holds the whole grid**, so advancing a generation is one row write rather than one write per cell. The obvious alternative — a row per cell, with `row`, `column` and `alive` columns — would turn a 100×100 board into 10,000 writes per generation for no benefit, since no query ever needs to address a single cell.

Bit-packing into a byte array was considered and rejected. At the board sizes this service targets the storage saving is irrelevant, and it trades a readable column and a trivially testable codec for a class whose defects corrupt stored state silently.

A round-trip test covers `serialize`/`deserialize` before any persistence code exists, along with a length check that rejects a string whose length does not equal `width × height`.

---

## 4. Simulation engine

### 4.1 Rules

B3/S23. A live cell with two or three live neighbours survives; a dead cell with exactly three live neighbours becomes live; all other cells die or stay dead. The neighbourhood is the eight surrounding cells, and out-of-bounds positions count as dead.

### 4.2 Implementation

A dense `boolean[]` of size `width × height`, double-buffered: each step computes into a second array and swaps. No per-cell allocation and no collections in the inner loop.

Interior and border cells use the same bounds-checked neighbour count. Specialising the interior for speed was rejected as unjustified complexity at the board sizes this service targets.

### 4.3 Termination detection

Generations are walked forward with a `Map<String, Integer>` from encoded state to generation index:

- The next state equals the current state → **fixed point**, period 1. An all-dead board is reported as `EXTINCT` for clearer semantics.
- The next state is already present in the map → **cycle**, with `firstOccurrence` the stored index and `period` the difference between the current and stored indices.
- `maxGenerations` reached with neither condition met → **no conclusion**, returned as 422 with the number of generations attempted.

Floyd's and Brent's cycle-detection algorithms were considered. They use constant memory, but the map additionally yields the cycle entry point and period directly, and memory is already bounded by `maxGenerations`.

---

## 5. API

Base path `/api/v1`. JSON throughout. Errors follow RFC 7807 (`application/problem+json`).

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/boards` | Upload a board, return id | `201` + `Location` | `400` malformed or oversized |
| `GET` | `/boards/{id}` | Metadata and generation 0 | `200` | `404` |
| `GET` | `/boards/{id}/next` | One generation forward | `200` | `404` |
| `GET` | `/boards/{id}/generations/{n}` | State n generations away | `200` | `404`, `400` |
| `GET` | `/boards/{id}/final` | Final state | `200` with termination metadata | `404`, `422` |

- `/next` delegates to the same code path as `/generations/1`. It is exposed separately because the specification names it as a distinct capability.
- Responses carry `generation`, `width`, `height` and `cells`. `/final` additionally returns `terminationKind`, `firstOccurrenceGeneration`, `period` and `generationsComputed`.
- `maxGenerations` is an optional query parameter on `/final`, clamped server-side.
- Input is validated at the edge: positive dimensions, cell count matching the declared dimensions, and a configured cap on total cells to bound resource use.
- No `GET` mutates board state. The only writes outside `POST /boards` go to the memoisation cache and are not observable through the API.

DTOs are Java `record`s with a small number of static factory methods. No code-generation or mapping libraries are used.

---

## 6. Testing

### 6.1 Coverage by layer

1. **Engine unit tests**, no application context: block (still life), blinker (period 2), toad, beacon, glider translating and then dying at the boundary, empty board, single cell, fully live board, and 1×1 and 1×N shapes.
2. **Codec tests** — randomly generated boards survive a `serialize`/`deserialize` round trip unchanged; a string whose length does not match `width × height` is rejected.
3. **Termination tests** — fixed point, cycle of period greater than 1, extinction, and a board that exceeds `maxGenerations` (with a small configured limit to keep the test fast).
4. **Repository tests** against a temporary-file SQLite database.
5. **API integration tests** using `@SpringBootTest` and `MockMvc`, covering every endpoint in §5 and each error path.
6. **Restart test** — a board is created and advanced, the application context is shut down, a new context is started against the same database file, and the board and its cached generations are asserted intact. This is the direct test of the durability requirement in §1.2.

### 6.2 Traceability

| Requirement | Covered by |
|---|---|
| F1 Upload board | `BoardApiTest#createBoardReturnsIdAndLocation` |
| F2 Next state | `BoardApiTest#nextReturnsFollowingGeneration` |
| F3 N generations away | `BoardApiTest#generationsAtIndexReturnsExpectedState` |
| F4 Final state | `BoardApiTest#finalReturnsTerminationMetadata` |
| F4 No conclusion | `BoardApiTest#finalReturns422WhenLimitExceeded` |
| Durability | `RestartPersistenceTest#stateSurvivesContextRestart` |
| Rules correctness | `LifeEngineTest` (all patterns) |
| Encoding integrity | `StateCodecTest#roundTripPreservesBoard` |

*(Method names to be confirmed against the final code.)*

A JaCoCo report is produced by the build, targeting 85% or above on `domain` and `service`. Full coverage of configuration and DTO accessors was not pursued, as it does not increase confidence in behaviour.

### 6.3 Running the examples

`requests.http` walks the full happy path end to end and serves as the API documentation. No OpenAPI tooling is included.

---

## 7. Possible extensions

- Infinite or toroidal topology as a per-board option.
- RLE pattern import for standard Game of Life pattern files.
- A sparse representation for large, mostly-dead grids, and HashLife for very deep generation counts.
- A server-backed datastore behind the existing `BoardRepository` interface, for horizontal scale.
- Rate limiting and request quotas.

## 8. Concurrency note

Two concurrent requests may compute the same uncached generation simultaneously. The resulting write race is benign: generation rows are immutable for a given `(board_id, idx)`, and inserts are idempotent, so either writer produces the same row. No locking is required.
