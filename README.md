# AiMob

AiMob is a Paper/Folia plugin that spawns a configurable protected mob and lets players chat with it through an OpenAI-compatible or Ollama endpoint.

## Requirements

- Minecraft Java 26.3 server (Paper or Folia)
- Java 25 runtime
- The committed Gradle 9.9.2 wrapper (Java 25 is needed to build)

The build defaults to Paper's documented `io.papermc.paper:paper-api:26.3.build.+`. To compile against Folia's API instead, set `paperApiGroup=dev.folia` and `paperApiArtifact=folia-api`; check the current stable build at [PaperMC's Maven repository](https://repo.papermc.io/repository/maven-public/). The API version is controlled by `paperApiVersion` in `gradle.properties`.

## Build

Build with `./gradlew build`. The plugin jar is written to `build/libs/AiMob-<version>.jar`. The wrapper can be regenerated with `gradle wrapper --gradle-version 9.9.2`. For a local server, copy the jar into `plugins/`, start the server, set the provider and API credentials in `plugins/AiMob/config.yml`, then use `/aimob spawn Guide`.

## Commands

- `/aimob spawn [name]` creates a mob at the player's location.
- `/aimob list` lists loaded plugin mobs; `/aimob remove <name>` removes one.
- `/aimob stick` gives the protected-mob removal stick; `/aimob reload` reloads configuration.
- Right-click a mob to chat; use shift-right-click with the removal stick to delete it. Type `exit` to end a chat.

Admin commands require `aimob.admin`; chatting requires `aimob.chat` (granted by default).

## Release

Push a version tag to trigger the release workflow: `git tag v1.0.0 && git push origin v1.0.0`. GitHub Actions builds the jar and attaches the single plugin jar to an automatically drafted GitHub Release. The build workflow also runs on pushes and pull requests to `main` or `master`.

## Notes

Protection only blocks player and player-projectile damage according to `protection.mode`, plus selected movement interactions. Environmental damage, void damage, and `/kill` can still remove a mob. Mob removal is intended through `/aimob remove <name>` or the shift-right-click removal stick. The optional `chat.ui-mode: dialog` opens a Paper text-input dialog and falls back to chat input if dialog creation is unavailable.
