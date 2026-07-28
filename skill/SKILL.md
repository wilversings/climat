---
name: using-climat
description: >-
  How to author Climat `.cli` macro files and drive the `climat` CLI. Use this
  whenever you need to write, install, run, validate, or debug a Climat toolchain
  (a `.cli` DSL file) — including questions about subcommands, parameters (arg/flag),
  optionals/defaults, constants, string/action interpolation and mappings, aliases,
  docstrings/help, `act sh` vs `act js`, and the `install`/`run`/`exec`/`runGlobal`/
  `validate`/`list`/`uninstall`/`purge` commands.
---

# Using Climat

Climat ("CLi MAcro Tree") turns a declarative `.cli` file into a real CLI command.
You write a *toolchain* — a tree of subcommands with typed parameters and templated
shell/JS actions — and install it so it behaves like any other command on `PATH`.

Implemented in Kotlin/JS, shipped as the npm package `climat`. Install the tool with
`npm i -g climat`. This skill is about *authoring and running toolchains*, not building
the Climat compiler itself (for that, see `AGENTS.md`).

## The `climat` CLI

Climat's own CLI is itself written in the DSL (see `src/main/kotlin/Manifest.kt`), so it
is the canonical reference. Commands:

| Command | Purpose |
| --- | --- |
| `climat install <path-or-url>` (alias `i`) | Globally install the toolchain in a `.cli` file. Accepts a local path or `http(s)://` URL. |
| `climat run -- <args...>` | Run the toolchain from the nearest manifest found up the directory tree (project-local, no install). |
| `climat exec <path> [--skipValidation] -- <args...>` (alias `p`) | Run the toolchain at an explicit manifest path. |
| `climat runGlobal <name> [shellPath] -- <args...>` | Run a globally installed toolchain by name. |
| `climat validate <path>` | Validate a `.cli` file without installing it. |
| `climat list` (aliases `ls`, `l`) | List installed toolchains. |
| `climat uninstall <name>` | Remove an installed toolchain. |
| `climat purge` | Remove all installed toolchains and Climat clutter (prompts y/n). |
| `climat --version` (alias `-v`) | Print the installed Climat version. |

`install` may need `sudo` since it writes to a global location (e.g. `sudo climat install sgit.cli`).
Everything after `--` for `run`/`exec`/`runGlobal` is passed through to the toolchain as its
arguments (this is the `@allow-unmatched` / `__UNMATCHED` mechanism, below).

**Fastest authoring loop:** `climat validate my.cli` to check structure, then
`climat exec my.cli -- <args>` to run it without a global install.

## DSL structure

A file defines exactly one root toolchain: an identifier, an optional parameter list, and a
body of statements. Statements are `sub` (subcommand), `act` (action), or `const` (constant).

```cli
my-toolchain(location l: arg?) {   // root name + params
  const greeting = "Hello"          // a constant
  act sh { echo @{greeting} from @{location} }   // default action

  sub child {                       // a subcommand
    act sh { echo Child }
  }
}
```

Invocation walks the tree: `my-toolchain child` runs the `child` sub's action. A node's
`act` runs when the arguments resolve to that node. Comments use `//`.

### Parameters

Declared in `(...)` after a name, comma-separated: `name shortName: type`.

- **Types:** `arg` (takes a value) or `flag` (boolean, no value).
- **Short name is optional:** `location l: arg` allows `--location X` and `-l X`; `location: arg` allows only `--location X`.
- **Optional arg:** append `?` → `location l: arg?`. If omitted, interpolates to empty.
- **Default value:** `location l: arg? = "the other side"`. Used when the flag is omitted.
- **Flags:** `goodDay: flag` → present = `true`, absent = `false`.

Parameters are **scoped to the node and all its descendants** — a param on the root or a
parent sub is usable in any child's action. Positional vs named: in these examples args are
passed by name (`--location X` / `-l X`); a bare positional value binds to the node's arg
(see integration tests where `hello-world Cluj-Napoca` fills `location`).

### Constants

`const name = "value"`. Scoped like parameters (visible to descendants). Values are string
templates and may interpolate other constants:

```cli
const my = "My"
const my-const = "@{my} Dear Constant"
```

### Actions

Each executable node has one `act`. Three forms:

- **Shell:** `act sh { echo hello }` — a shell command template. Braces inside don't need
  escaping; Climat matches the run of `{` used to open (so `act sh {{ ... }}` allows lone `}`).
- **JavaScript:** `act js {{ console.log(params.location) }}` — a JS body. Read parameters
  via `params` (e.g. `params.location`, or `params.get("name")` / `params.get("__UNMATCHED")`).
- **`act scope-params`** — declares a node as a parameter scope without its own runnable action.

### Interpolation and mappings

Inside `act sh {…}` and string templates, `@{...}` interpolates a parameter or constant.

- **Plain:** `@{location}` → the value (empty if an optional is unset).
- **Negation (flags):** `@{!goodDay ? "..."}`.
- **Conditional mapping (flag):** `@{goodDay ? "today-is-a-good-day"}` — emits the string only
  when the flag is true; emits nothing when false.
- **Value mapping (arg):** `@{dayOfTheWeek ? "--today-is={}"}` — `{}` is a placeholder for the
  arg's value, so `Tuesday` becomes `--today-is=Tuesday`. Emits nothing if the arg is unset.

Example (`mapping-flags.cli`):
```cli
hello-world(goodDay: flag) {
  sub bar { act sh { echo Hello World @{goodDay ? "today-is-a-good-day"} } }
  sub baz { act sh { echo Hello World @{!goodDay ? "today-is-NOT-a-good-day"} } }
}
```

### Aliases

Modifiers placed before a `sub` (or the root) add alternate names:

```cli
@aliases(ct child)   // multiple aliases
@alias(cld)          // single alias
sub child-toolchain { act sh { echo Child } }
```

At the root level, `@alias`/`@aliases` create alternate install names for the whole toolchain.

### Docstrings and help

A `""" ... """` block before the root or a `sub` documents it; `@param name description` tags
document parameters. These power generated `--help` output:

```cli
"""
Globally installs the toolchain present at the provided path
@param pathToManifest the path to toolchain manifest
"""
@alias(i)
sub install(pathToManifest p: arg) { act js {{ climat.install(params.get("pathToManifest")) }} }
```

### Other modifiers

- `@allow-unmatched` — lets the node accept extra, undeclared arguments; they arrive as the
  special `__UNMATCHED` parameter (used for pass-through, e.g. `climat run -- git push`).
- `@seal` / `@shift` — reserved modifiers; functionality is not yet implemented (pre-release).

## Validation

Climat validates a toolchain on install/run (skip with `--skipValidation` on `exec`). Rules live
in `climatEngine/.../validation/validations/` and include duplicate ref names, shadowed params,
and undefined params. Run `climat validate <path>` to check a file explicitly. Warnings print but
don't block; errors do.

## Worked example

```cli
sgit {
  sub acp(amend a: flag) {
    act sh {
      git add . &&
      git commit @{amend ? "--amend"} &&
      git push @{amend ? "--force"}
    }
  }
  sub cf(branch: arg, force f: flag) {
    act sh { git checkout feature/@{branch} @{force ? "--force"} }
  }
}
```

`sudo climat install sgit.cli` then:
- `sgit acp` → add, commit, push
- `sgit acp -a` → add, commit `--amend`, push `--force`
- `sgit cf myFeature` → `git checkout feature/myFeature`

## Quick reference of examples in this repo

Real, tested `.cli` files live in `integrationTests/src/test/resources/documentationExamples/`
(exercised by `DocumentationExamplesTest.kt`): `subcommands`, `aliases`, `constants`,
`constants-scope`, `interpolation`, `parameters`, `parameters-scope`, `optionals`, `defaults`,
`mapping-flags`, `mapping-args`, `javascript`. `test-toolchain.cli` shows a fuller tree with
nested subs, defaults, aliases, and both `act sh` and `act js`. When unsure of exact syntax,
read those files and the grammar at `climatEngine/src/antlr/DslParser.g4` / `DslLexer.g4`.
