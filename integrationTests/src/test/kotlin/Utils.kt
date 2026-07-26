
fun runClimat(args: String): String {
    return run("climat $args")
}

fun run(args: String): String {
    return child_process.execSync(args).toString("utf8").trim()
}

class Outcome(
    val status: Int,
    val stdout: String,
    val stderr: String,
)

@JsModule("child_process")
@JsNonModule
private external object Cp {
    fun spawnSync(
        command: String,
        args: Array<String>,
        options: dynamic,
    ): dynamic
}

/**
 * Runs a command capturing stdout, stderr and the exit status separately.
 *
 * [run] cannot express the microshell fidelity tests: it throws on a non-zero status and gives no
 * access to stderr — but "stderr is empty" and "the status is exactly 141" are the assertions that
 * matter there.
 *
 * The timeout is load-bearing: a botched pipeline teardown hangs forever, and this turns that into
 * a failing assertion rather than a wedged CI job.
 */
fun runDetailed(vararg argv: String): Outcome {
    val result = Cp.spawnSync(
        argv.first(),
        argv.drop(1).toTypedArray(),
        js("({encoding: 'utf8', timeout: 10000})"),
    )
    val status = result.status as? Int
        ?: throw AssertionError(
            "`${argv.joinToString(" ")}` did not exit normally — most likely it timed out, " +
                "which means a pipeline never terminated",
        )
    return Outcome(
        status = status,
        stdout = (result.stdout as? String).orEmpty().trim(),
        stderr = (result.stderr as? String).orEmpty().trim(),
    )
}