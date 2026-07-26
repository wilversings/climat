package e2e.features

import com.climat.microshell.Op
import com.climat.microshell.RAndOr
import com.climat.microshell.RCommand
import com.climat.microshell.RPipeline
import com.climat.microshell.RSeq
import e2e.E2ETestBase
import kotlin.test.Test

private fun cmd(vararg argv: String) = RCommand(emptyList(), argv.toList())

class Microshell : E2ETestBase() {

    @Test
    fun resolvesRefsIntoArgvEntries() {
        """
        tool {
          sub greet(name: arg, loud l: flag, nick: arg?) {
            act microsh { echo @{name} @{loud ? "--loud"} @{nick ? "--nick={}"} }
          }
        }
        """.assertPlans(
            "greet world" to
                cmd("echo", "world"),

            "greet world --loud" to
                cmd("echo", "world", "--loud"),

            "greet world --nick w" to
                cmd("echo", "world", "--nick=w"),
        )
    }

    @Test
    fun anInterpolatedValueIsAlwaysExactlyOneArgument() {
        // The reason microshell parses before interpolating: a value can never become syntax.
        val dsl = """
        tool {
          sub run(msg: arg) {
            act microsh { echo @{msg} }
          }
        }
        """
        dsl.assertPlansForArgv(
            listOf("run", "hello world") to
                cmd("echo", "hello world"),

            listOf("run", "a; rm -rf /") to
                cmd("echo", "a; rm -rf /"),

            listOf("run", "a | b && c") to
                cmd("echo", "a | b && c"),

            listOf("run", "\$(whoami)") to
                cmd("echo", "\$(whoami)"),

            listOf("run", "'quoted'") to
                cmd("echo", "'quoted'"),

            listOf("run", "  leading and trailing  ") to
                cmd("echo", "  leading and trailing  "),
        )
    }

    @Test
    fun anUnsetMappingDropsTheWholeArgument() {
        // Not an empty argv entry — the argument disappears.
        """
        tool {
          sub run(a: arg?, f: flag) {
            act microsh { cmd @{a ? "--a={}"} @{f ? "--f"} tail }
          }
        }
        """.assertPlans(
            "run" to cmd("cmd", "tail"),
            "run --a x" to cmd("cmd", "--a=x", "tail"),
            "run --f" to cmd("cmd", "--f", "tail"),
        )
    }

    @Test
    fun anUnsetOptionalArgDropsItsWord() {
        """
        tool {
          sub run(a: arg?) {
            act microsh { cmd @{a} tail }
          }
        }
        """.assertPlans(
            "run" to cmd("cmd", "tail"),
            "run --a x" to cmd("cmd", "x", "tail"),
        )
    }

    @Test
    fun buildsTheFullOperatorSet() {
        """
        tool {
          sub p { act microsh { a | b } }
          sub andOr { act microsh { a && b || c } }
          sub seq { act microsh { a ; b } }
          sub grouped { act microsh { (a || b) && c } }
          sub env { act microsh { KEY=value cmd arg } }
          sub quoted { act microsh { echo 'two words' } }
        }
        """.assertPlans(
            "p" to RPipeline(listOf(cmd("a"), cmd("b"))),
            "andOr" to RAndOr(RAndOr(cmd("a"), Op.And, cmd("b")), Op.Or, cmd("c")),
            "seq" to RSeq(listOf(cmd("a"), cmd("b"))),
            "grouped" to RAndOr(RAndOr(cmd("a"), Op.Or, cmd("b")), Op.And, cmd("c")),
            "env" to RCommand(listOf("KEY" to "value"), listOf("cmd", "arg")),
            "quoted" to cmd("echo", "two words"),
        )
    }

    @Test
    fun holesResolveInsidePipelinesAndAssignments() {
        """
        tool {
          sub run(x: arg) {
            act microsh { KEY=@{x} producer @{x} | consumer --v=@{x} }
          }
        }
        """.assertPlansForArgv(
            listOf("run", "a b") to RPipeline(
                listOf(
                    RCommand(listOf("KEY" to "a b"), listOf("producer", "a b")),
                    cmd("consumer", "--v=a b"),
                ),
            ),
        )
    }

    @Test
    fun multiLineBodiesTreatNewlinesAsWhitespace() {
        """
        tool {
          sub run {
            act microsh {
              echo one
              two
            }
          }
        }
        """.assertPlans("run" to cmd("echo", "one", "two"))
    }

    @Test
    fun reportsMalformedBodiesAtParseTime() {
        listOf(
            "act microsh { echo 'unterminated }" to "Unterminated",
            "act microsh { (a && b }" to "Unbalanced",
            "act microsh { a && }" to "expected a command",
            "act microsh { A=1 }" to "must be followed by a command",
            "act microsh { echo ${'$'}HOME }" to "not supported",
            "act microsh { a > b }" to "not supported",
        ).forEach { (action, expected) ->
            """
            tool {
              sub run { $action }
            }
            """.assertThrows<Throwable>(
                "run" to assertMessageContains(expected),
            )
        }
    }
}
