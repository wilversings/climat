package e2e

import com.climat.library.commandParser.execute
import com.climat.library.domain.action.ActionValueBase
import com.climat.library.domain.action.LiteralText
import com.climat.library.domain.action.MicroshellActionValue
import com.climat.library.domain.action.ResolvedValue
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

abstract class E2ETestBase {

    private fun execAction(argv: Array<String>, cliDsl: String): ActionValueBase<*>? {
        var ans: ActionValueBase<*>? = null
        execute(argv, cliDsl, { act, _ ->
            ans = act
        })
        return ans
    }

    private fun execAction(args: String, cliDsl: String): ActionValueBase<*>? =
        execAction(
            if (args.isEmpty()) emptyArray() else args.split(" ").toTypedArray(),
            cliDsl,
        )

    private fun exec(args: String, cliDsl: String): String? =
        execAction(args, cliDsl)?.value as? String

    /**
     * Asserts the segments an `act microsh` body resolves to, tagged `t:` for literal DSL text and
     * `v:` for a resolved ref value.
     *
     * The engine does not parse shell syntax, so the boundary between text and value *is* the
     * contract: it is what stops a value from ever being read as syntax downstream.
     */
    protected fun String.assertSegments(vararg commandToSegments: Pair<String, List<String>>): String {
        commandToSegments.forEach { (command, expected) ->
            assertEquals(expected, tagged(execAction(command, this)), "Unexpected segments for `$command`")
        }
        return this
    }

    /**
     * Same, but takes argv directly. Necessary for values containing spaces, which the
     * `split(" ")` form above cannot express — and those are exactly the interesting cases.
     */
    protected fun String.assertSegmentsForArgv(vararg argvToSegments: Pair<List<String>, List<String>>): String {
        argvToSegments.forEach { (argv, expected) ->
            val action = execAction(argv.toTypedArray(), this)
            assertEquals(expected, tagged(action), "Unexpected segments for argv $argv")
        }
        return this
    }

    private fun tagged(action: ActionValueBase<*>?): List<String>? =
        (action as? MicroshellActionValue)?.segments?.map {
            when (it) {
                is LiteralText -> "t:${it.text}"
                is ResolvedValue -> "v:${it.value}"
            }
        }

    protected fun String.assertResults(vararg commandToResult: Pair<String, String?>): String {
        commandToResult.forEach { (command, expectedResult) ->
            val actualResult = exec(command, this)
            assertEquals(expectedResult?.trim(), actualResult?.trim(), "Unexpected result for command `$command`")
        }
        return this
    }

    protected fun <T : Throwable> String.assertThrows(
        vararg commandToResult: Pair<String, (T) -> Unit>,
        print: Boolean = false
    ): String {
        commandToResult.forEach { (command, exceptionHandler) ->
            val ex = assertFails("Command `$command` did not throw any exception") {
                val res = exec(command, this)
            } as? T
            if (ex != null) {
                if (print) println(ex.message)
                exceptionHandler(ex)
            } else {
                fail("Command `$command` did not throw the expected exception type")
            }
        }
        return this
    }

    protected fun <T : Throwable> assertMessageContains(vararg tokens: String): (T) -> Unit {
        return {
            val message = it.message.orEmpty()
            tokens.forEach { assertContains(message, it) }
        }
    }
}
