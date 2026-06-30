# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A load-test / verification client for an IoT message ingestion server (the target server lives in a separate repo: https://github.com/ltoinel/IoT-Development-Challenge). The client sends batches of randomized sensor-reading JSON messages over HTTP, then queries the server's synthesis (aggregation) endpoint and compares the server's answer against an in-memory ground truth it computed locally.

## Build & run

```
mvn -B -ntp verify          # compile + package + run tests (what CI runs, JDK 25)
mvn package                 # build the shaded/uber jar (main class: com.capgemini.csd.hackaton.client.Main)
```

Run the client (after `mvn package`, jar is in `target/`):

```
java -jar target/hackaton-iot-java-client-1.0-SNAPSHOT-uber.jar client -host <host> -port <port> [options...]
java -jar target/hackaton-iot-java-client-1.0-SNAPSHOT-uber.jar help client   # list all options
```

There is no separate test suite currently (no `src/test`); `mvn verify` just builds.

## Architecture

CLI entry point is `Main`, built on the `io.airlift.airline` CLI library. It registers one real subcommand, `ExecutionClient` (command name `client`), plus the built-in `Help` command.

Flow when `client` runs (`ExecutionClient.run`):
1. Configure static tuning knobs on `AbstractClient`/`ClientAsyncHTTP` (`MAX_VALUE`, `SENSOR_TYPES`, `THREADS`) from CLI options.
2. If `-limites` is set, run edge-case checks (`testLimites`): huge values near `Long.MAX_VALUE`/`MIN_VALUE` to verify average computation doesn't overflow, and duplicate-message-id checks (sequential and concurrent) to verify the server deduplicates by id.
3. Send `n` batches of `m` messages each (`ClientAsyncHTTP.sendMessages`), via `org.asynchttpclient`, all messages in a batch fired concurrently and awaited.
4. After each batch, optionally verify synthesis: pick random `(start, duration)` windows, call the server's `/messages/synthesis` endpoint, and compare to a locally-computed synthesis for the same window.

Key classes (all in `com.capgemini.csd.hackaton.client`):
- `Client` — interface for sending messages and querying synthesis.
- `AbstractClient` — shared logic: random message generation (id/timestamp/sensorType/value), and **local ground-truth indexing**. Every generated message is also recorded into a `TreeMap<UUID, UUID>` keyed by `(timestamp, sequence)` → `(sensorType, value)`, packed into UUIDs as a space-efficient sortable composite key. `getSyntheseLocale` does a `subMap` range query over this map and reduces it via `Summary`'s `Collector` to produce local min/max/avg per sensor type, which is the expected value to diff against the server's response.
- `ClientAsyncHTTP` — HTTP implementation of `Client` using `async-http-client`; POSTs to `/messages`, GETs `/messages/synthesis?timestamp=...&duration=...`.
- `Summary` — accumulates count/min/max/total (as `BigDecimal`, to avoid `long` overflow when summing) for one sensor type; average is `total / count` rounded HALF_UP to 2 decimals.
- `ExecutionClient` — the CLI command: owns all `-option` flags, orchestrates batches, edge-case tests, and the comparison logic (`testEq`/`assertEquals`) between local `Summary` objects and the server's JSON response (parsed via JSR-353 `javax.json`).

Message JSON shape sent to the server: `{ "id": <string>, "timestamp": <ISO8601>, "sensorType": <int>, "value": <long> }`. Timestamps are serialized with Joda-Time `ISODateTimeFormat`; when `randomTime` is true, a random jitter of ±10s from "now" is applied (deliberately, to differ from a typical Gatling-style fixed-clock load test).

Values are compared as `BigDecimal` (via `stripTrailingZeros`) to absorb int/decimal formatting differences between client and server JSON.
