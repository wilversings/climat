package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

private fun t(text: String) = "t:$text"

private fun v(value: String) = "v:$value"

/**
 * The engine's half of `act microsh`: resolve refs, and keep straight which parts of the body are
 * author-written text and which are values. It deliberately does **not** parse shell syntax — that
 * happens in the microshell binary at run time — so there is nothing here about pipes or operators.
 */
class Microshell : E2ETestBase() {

    @Test
    fun resolvesRefsIntoValueSegments() {
        """
        tool {
          sub greet(name: arg, loud l: flag, nick: arg?) {
            act microsh { echo @{name} @{loud ? "--loud"} @{nick ? "--nick={}"} }
          }
        }
        """.assertSegments(
            "greet world" to
                listOf(t(" echo "), v("world"), t(" "), v(""), t(" "), v(""), t(" ")),

            "greet world --loud" to
                listOf(t(" echo "), v("world"), t(" "), v("--loud"), t(" "), v(""), t(" ")),

            "greet world --nick w" to
                listOf(t(" echo "), v("world"), t(" "), v(""), t(" "), v("--nick=w"), t(" ")),
        )
    }

    @Test
    fun aValueStaysOneSegmentWhateverItContains() {
        // The engine's contribution to injection safety: hostile text lands in a `v:` segment and
        // never merges into the surrounding literal text.
        val dsl = """
        tool {
          sub run(msg: arg) {
            act microsh { echo @{msg} }
          }
        }
        """
        listOf(
            "hello world",
            "a; rm -rf /",
            "a | b && c",
            "${'$'}(whoami)",
            "'quoted'",
            "  leading and trailing  ",
        ).forEach { hostile ->
            dsl.assertSegmentsForArgv(
                listOf("run", hostile) to listOf(t(" echo "), v(hostile), t(" ")),
            )
        }
    }

    @Test
    fun valuesAreNotShellQuoted() {
        // `act sh` wraps arg values in single quotes so the shell does not re-lex them; the
        // microshell path must not, because the value is handed over as a discrete segment.
        """
        tool {
          sub sh-version(msg: arg) { act sh { echo @{msg} } }
          sub microsh-version(msg: arg) { act microsh { echo @{msg} } }
        }
        """.assertResults(
            "sh-version hello" to "echo 'hello'",
        ).assertSegments(
            "microsh-version hello" to listOf(t(" echo "), v("hello"), t(" ")),
        )
    }

    @Test
    fun anUnsetRefResolvesToAnEmptyValue() {
        // Empty rather than absent: dropping the word is the microshell's job, since only it knows
        // whether the value stands alone.
        """
        tool {
          sub run(a: arg?, f: flag) {
            act microsh { cmd @{a ? "--a={}"} @{f ? "--f"} tail }
          }
        }
        """.assertSegments(
            "run" to listOf(t(" cmd "), v(""), t(" "), v(""), t(" tail ")),
            "run --a x" to listOf(t(" cmd "), v("--a=x"), t(" "), v(""), t(" tail ")),
            "run --f" to listOf(t(" cmd "), v(""), t(" "), v("--f"), t(" tail ")),
        )
    }

    @Test
    fun literalTextIsPassedThroughVerbatim() {
        // Not whitespace-collapsed: the microshell does its own tokenising, so the body must arrive
        // exactly as written.
        """
        tool {
          sub run {
            act microsh {
              echo one
              two
            }
          }
        }
        """.assertSegments(
            "run" to listOf(t("\n              echo one\n              two\n            ")),
        )
    }

    @Test
    fun shellSyntaxIsNotParsedByTheEngine() {
        // A body the microshell would reject still decodes fine here — validation moved to run time
        // on purpose, so the DSL has no opinion about shell syntax.
        """
        tool {
          sub broken { act microsh { (a && b } }
          sub alsoBroken { act microsh { echo ${'$'}HOME } }
        }
        """.assertSegments(
            "broken" to listOf(t(" (a && b ")),
            "alsoBroken" to listOf(t(" echo ${'$'}HOME ")),
        )
    }
}
