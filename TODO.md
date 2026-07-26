# Embedded microshell

In order to make the toolchains run everywhere, we need an internal shell. Not a fully fleged one, but one that offers basic functionality like:
- Pipe-ing (`command1 | command2`)
- && operator
- || operator
- ; semicolon to separate commands
- Passing environment variable by running `VAR=value command`
- Desambiguisation via parantheses (`(command1 | command2) && command3`)
- Existing bash functionality regarding environment variable interpolating, double quotes and single quotes

Changes:
- [x] Implement the microshell as a separate gradle project
- [ ] Use microshell as a bootstraping shell
- [x] Add usage to DSL `act microsh { echo "abc" }`
- [ ] Microshell should be the only one allowed for Windows.

## Status

Done: the `microshell` module (parser in `commonMain`, native executor in `nativeMain`) and the
`act microsh { ... }` action. Supports `|`, `&&`, `||`, `;`, `( )`, `VAR=value cmd` and literal
`'...'`.

The two languages are kept **decoupled**: `climatEngine` does not depend on `microshell` and never
parses shell syntax. It resolves refs and hands over tagged segments — literal DSL text and resolved
values (`t "echo " v "a; rm -rf /"`) — and the binary parses at run time. That is what keeps a value
from ever being read as syntax, and it means a malformed body is reported on use rather than at
`climat install`. Coupling them (e.g. moving the shell grammar into the ANTLR DSL) is a deliberate
future option, once both languages are mature.

Deferred from the original list:
- Bash-compatible `$VAR` expansion, double quotes and escapes were dropped for v1. `@{ref}` covers
  the real need: it always yields exactly one argument, so a value with spaces needs no quoting and
  an injected value can never be re-read as syntax.

Notes for the remaining two items:
- **Bootstrap shell** — the native binary makes this straightforward; `Unix.getScriptContent` in
  `src/main/kotlin/platform/Unix.kt` still writes a `#!/bin/bash` wrapper.
- **Windows** — needs a `mingwX64` target plus a Windows executor. The POSIX executor in
  `microshell/src/nativeMain` does not port: Windows has no `fork`, so it needs
  `CreatePipe`/`CreateProcess`. Windows CI was dropped in 7c5cd93.

Why the executor is native rather than Node: libuv implements stdio `'pipe'` as a Unix domain
socketpair, not `pipe(2)`. An early-exiting consumer therefore gives the producer `ECONNRESET`
rather than `SIGPIPE`, so `yes | head -3` prints `yes: standard output: Connection reset by peer`
and exits 1 where a real shell is silent and exits 141. Streaming through Node instead
(`a.stdout.pipe(b.stdin)`) never terminates at all. Neither is fixable from JS.