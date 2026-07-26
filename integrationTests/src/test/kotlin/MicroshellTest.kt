import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

/**
 * Exercises `act microsh` through the installed CLI, i.e. against real processes.
 *
 * The `fidelity` tests are the reason the microshell is a native binary at all: reimplementing
 * pipelines on Node streams either hangs or leaks an `ECONNRESET` diagnostic onto stderr, because
 * libuv wires stdio with a socketpair rather than `pipe(2)`. These assert the difference is gone.
 */
class MicroshellTest {

    private fun install() = runClimat("install kotlin/microshell.cli")

    @Test
    fun earlyExitingConsumerTerminatesSilently() {
        install()
        // With a naive Node `.pipe()` this never terminates and `runDetailed` times out; with a
        // socketpair it terminates but `yes` prints "Connection reset by peer".
        val outcome = runDetailed("microsh-test", "early-exit")

        outcome.stdout shouldBe "hello\nhello\nhello"
        outcome.stderr shouldBe ""
        outcome.status shouldBe 0

        // ...and identical to what a real shell produces.
        val reference = runDetailed("bash", "-c", "yes hello | head -3")
        outcome.stdout shouldBe reference.stdout
        outcome.stderr shouldBe reference.stderr
        outcome.status shouldBe reference.status
    }

    @Test
    fun consumerThatNeverReadsStillTerminates() {
        install()
        val outcome = runDetailed("microsh-test", "never-reads")

        outcome.stderr shouldBe ""
        outcome.status shouldBe 0
    }

    @Test
    fun interiorStageIsWiredCorrectly() {
        install()
        // A three-stage pipeline is where closing the wrong pipe end shows up: the middle stage
        // must not keep a reader open on the pipe it forwards to.
        val outcome = runDetailed("microsh-test", "three-stage")

        outcome.stdout shouldBe "100"
        outcome.stderr shouldBe ""
        outcome.status shouldBe 0
    }

    @Test
    fun operatorsBehaveLikeAShell() {
        install()
        run("microsh-test piped") shouldBe "bbc"
        run("microsh-test and-ok") shouldBe "and-ok"
        // `false` short-circuits `&&`, so its exit status (1) propagates rather than 0.
        val shortCircuited = runDetailed("microsh-test", "and-short-circuits")
        shortCircuited.stdout shouldBe ""
        shortCircuited.status shouldBe 1
        run("microsh-test or-fallback") shouldBe "or-fallback"
        run("microsh-test sequence") shouldBe "one\ntwo"
        run("microsh-test grouped") shouldBe "grouped-ok"
        run("microsh-test quoted") shouldBe "two words"
        run("microsh-test env-prefix") shouldContain "KEY=from-plan"
    }

    @Test
    fun exitStatusesMatchTheShellConvention() {
        install()
        runDetailed("microsh-test", "failing").status shouldBe 1
        runDetailed("microsh-test", "not-found").status shouldBe 127
        runDetailed("microsh-test", "not-found").stderr shouldContain "definitely-not-a-real-command"
    }

    @Test
    fun malformedBodiesAreReportedAtRunTime() {
        // Shell syntax belongs to the microshell, not the DSL, so `install` accepts these and the
        // error surfaces when the macro runs. This suite is the only thing guarding that path.
        install()

        val unbalanced = runDetailed("microsh-test", "unbalanced")
        unbalanced.status shouldBe 2
        unbalanced.stderr shouldContain "Unbalanced"

        val dollar = runDetailed("microsh-test", "dollar-expansion")
        dollar.status shouldBe 2
        dollar.stderr shouldContain "not supported"
    }

    @Test
    fun interpolatedValuesAreExactlyOneArgument() {
        install()
        // Each of these would be re-lexed into syntax by a real shell; here they stay inert.
        run("microsh-test echo-arg \"two words\"") shouldBe "two words"
        run("microsh-test echo-arg \"a; echo INJECTED\"") shouldBe "a; echo INJECTED"
        run("microsh-test echo-arg \"a | echo INJECTED\"") shouldBe "a | echo INJECTED"
        run("microsh-test echo-arg \"\\$(echo INJECTED)\"") shouldBe "\$(echo INJECTED)"
        run("microsh-test echo-arg \"'quoted'\"") shouldBe "'quoted'"
    }

    @Test
    fun unsetMappingsDropTheirArgument() {
        install()
        run("microsh-test mapped") shouldBe "tail"
        run("microsh-test mapped --name x") shouldBe "tail --name=x"
        run("microsh-test mapped --loud") shouldBe "tail --loud"
    }
}
