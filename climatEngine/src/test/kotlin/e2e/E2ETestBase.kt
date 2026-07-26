package e2e

import com.climat.library.commandParser.execute
import com.climat.library.domain.action.ActionValueBase
import com.climat.library.domain.action.MicroshellActionValue
import com.climat.microshell.ResolvedNode
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
     * Asserts the resolved microshell plan — the argv tree that actually gets executed, rather than
     * its string rendering, so word boundaries are part of what is being checked.
     */
    protected fun String.assertPlans(vararg commandToPlan: Pair<String, ResolvedNode>): String {
        commandToPlan.forEach { (command, expectedPlan) ->
            val plan = (execAction(command, this) as? MicroshellActionValue)?.plan
            assertEquals(expectedPlan, plan, "Unexpected plan for command `$command`")
        }
        return this
    }

    /**
     * Same, but takes argv directly. Necessary for values containing spaces, which the
     * `split(" ")` form above cannot express — and those are exactly the interesting cases.
     */
    protected fun String.assertPlansForArgv(vararg argvToPlan: Pair<List<String>, ResolvedNode>): String {
        argvToPlan.forEach { (argv, expectedPlan) ->
            val plan = (execAction(argv.toTypedArray(), this) as? MicroshellActionValue)?.plan
            assertEquals(expectedPlan, plan, "Unexpected plan for argv $argv")
        }
        return this
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
