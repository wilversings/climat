# AGENTS.md

## Project overview

Climat ("CLi MAcro Tree") is a Kotlin Multiplatform (JS target) tool that generates powerful CLI macros from a
declarative DSL (`.cli` files). Users write toolchains with subcommands, flags, and templated shell actions;
Climat parses the DSL (ANTLR grammar) and installs it as a real CLI command.

- Distributed as an npm package (`climat`), but implemented entirely in Kotlin, compiled to JS via Kotlin/JS.
- Language: Kotlin (multiplatform, JS-only binaries).
- Build tool: Gradle (Kotlin DSL).

## Module layout

- `src/main/kotlin` — the CLI entry point / runtime (`Main.kt`, `ClimatCli.kt`, `AsyncClimatCli.kt`, install logic,
  platform-specific code under `platform/`, console output under `output/`).
- `microshell/` — the embedded shell behind `act microsh { ... }`. `commonMain` holds the parser and
  plan model (shared with the JS side so bodies are parsed at DSL-decode time); `nativeMain` holds
  the executor, which uses real `pipe(2)`/`fork`/`dup2`/`execvp` via `platform.posix`. Built for
  `linuxX64`, `macosX64` and `macosArm64`; all three binaries are bundled into the npm package under
  `msh/`. Node cannot host this: libuv wires stdio `'pipe'` as a Unix socketpair, so an early-exiting
  consumer gives the producer `ECONNRESET` (visible stderr noise, wrong status) instead of `SIGPIPE`.
- `climatEngine/` — the core library: DSL parsing (ANTLR grammar in `climatEngine/src/antlr/*.g4`), the
  `dslParser` (turns parsed DSL into a domain model under `domain`), `commandParser` (turns parsed CLI args +
  domain model into an executable toolchain), and `validation` (structural checks on a parsed DSL tree, e.g.
  `DuplicateRefNames`, `ShadowedParams`, `UndefinedParams`). This is the reusable, engine-only piece with no CLI
  glue.
- `integrationTests/` — end-to-end tests that install the built `climat` CLI and exercise it as a real user would
  (via Mocha on Node).
- `docs/` — Docusaurus site (separate npm project) for https://climat-project.github.io. Not part of the Gradle
  build.
- `design/` — static design assets (logo, etc.), not code.

## Build, test, and run

Requires JDK 21 and Node.js (>=13.14, tested up to 22.x).

```sh
./gradlew build                              # full build (includes unit tests + integration tests)
./gradlew build -x integrationTests:jsNodeTest   # build + unit tests, skip integration tests
./gradlew climatEngine:jsNodeTest            # run climatEngine unit tests only
./gradlew integrationTests:jsNodeTest        # run integration tests (requires climat installed globally, see below)
```

Integration tests expect the just-built CLI to be installed globally first (mirrors `.github/workflows/build.yml`):

```sh
npm install -g build/dist/js/productionExecutable
./gradlew integrationTests:jsNodeTest
```

Linting uses ktlint via the Gradle plugin (`./gradlew ktlintCheck` / `./gradlew ktlintFormat`).

The DSL grammar (`climatEngine/src/antlr/DslLexer.g4`, `DslParser.g4`) is compiled to Kotlin sources by the
`generateKotlinCommonGrammarSource` Gradle task, which runs automatically before `compileKotlinJs`. Regenerate by
re-running the build after editing a `.g4` file — don't hand-edit generated sources under
`climatEngine/build/generated-src`.

## Conventions

- Keep `climatEngine` free of CLI/Node-specific concerns; CLI wiring belongs in the root `src/main/kotlin`
  module.
- New DSL validation rules go in `climatEngine/src/main/kotlin/com/climat/library/validation/validations/` and
  should have a corresponding `ValidationCode`.
- Prefer adding/extending unit tests under `climatEngine/src/test/kotlin` for parser/domain logic, and
  integration tests under `integrationTests/src/test/kotlin` for behavior that depends on the installed CLI
  (installation, real subprocess execution, etc.).

## Contribution norms (see CONTRIBUTING.md)

- Raise a Feature Request, Bug Report, or Question before starting non-trivial work — avoids redundant effort.
- Avoid trivial-only changes (formatting, renames) unless bundled with a substantive contribution.
- Valuable contributions: feature implementation, bugfixes, performance/architecture improvements, documentation,
  and unit/integration tests.

## Notes / gotchas

- This is a pre-release project ("Everything is subject to change") — expect API/DSL churn.
