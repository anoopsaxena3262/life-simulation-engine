# Conway's Game of Life API

A REST API implementing Conway's Game of Life, with board state persisted across restarts.

See [DESIGN.md](DESIGN.md) for the architecture, the decisions behind it, and the
requirements traceability table.

## Running

Requires Java 25 and Maven.

```
mvn spring-boot:run
```

The service listens on `http://localhost:8080`. A SQLite database is created at
`data/game-of-life.db` on first start; no other setup is required.

## Tests

```
mvn test
```

A JaCoCo coverage report is written to `target/site/jacoco/index.html`.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/boards` | Upload a board, returns its id |
| `GET` | `/api/v1/boards/{id}` | Board metadata and generation 0 |
| `GET` | `/api/v1/boards/{id}/next` | One generation forward |
| `GET` | `/api/v1/boards/{id}/generations/{n}` | The state n generations away |
| `GET` | `/api/v1/boards/{id}/final` | Final state and how the board concluded |

`requests.http` walks the full happy path end to end.

## Scope

Authentication and authorisation are out of scope for this exercise. Other omissions,
and the reasoning behind them, are recorded in DESIGN.md.
