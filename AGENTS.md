# Repository Guidelines

## Project Structure

This is a Java 25 Paper/Folia plugin built with Gradle Kotlin DSL. Production Java code is under `src/main/java/dev/aimob/`, grouped into `ai/`, `command/`, `listener/`, `service/`, and `util/`. Plugin metadata and defaults live in `src/main/resources/`. GitHub Actions workflows are in `.github/workflows/`; build output belongs in `build/` and is ignored.

## Build and Development

- `./gradlew build` compiles the plugin and creates `build/libs/AiMob-<version>.jar`.
- `./gradlew clean build` performs a clean build.
- Use Java 25 and the committed Gradle 9.1 wrapper. The Paper API coordinate is configured by `paperApiGroup`, `paperApiArtifact`, and `paperApiVersion` in `gradle.properties`.

There is no test suite yet. If adding tests, use Gradle's `src/test/java` layout and document any test dependencies and commands here.

## Style and Folia Safety

Use four spaces for Java indentation, descriptive class and method names, and one public top-level class per file. Keep messages in Adventure components and MiniMessage templates in `config.yml`. Never use `BukkitScheduler` or `Bukkit.getScheduler()`. Use entity/player schedulers before accessing their state from asynchronous callbacks; use async schedulers for timers and `HttpClient.sendAsync` for network calls. Shared state across regions must use concurrent collections or atomics.

## Configuration and Secrets

Keep provider keys out of commits. Configure credentials in the server's `plugins/AiMob/config.yml` or use `AIMOB_API_KEY`. Preserve safe defaults and update the commented config and README when adding settings or commands.

## Commits and Pull Requests

Use concise imperative commit subjects, such as `Add Ollama request support`. Pull requests should describe behavior and compatibility changes, link relevant issues, and report `./gradlew build` results. Include screenshots or logs when they clarify user-visible behavior.
