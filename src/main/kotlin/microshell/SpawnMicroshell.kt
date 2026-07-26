package com.climat.microshell

import com.climat.Path
import com.climat.jsObjectOf
import process
import kotlin.js.Promise

@JsModule("child_process")
@JsNonModule
private external object NodeChildProcess {
    fun spawn(
        command: String,
        args: Array<String>,
        options: dynamic,
    ): dynamic
}

/**
 * Directory of the emitted bundle. Webpack is configured with `node.__dirname = false`, so this
 * keeps Node's real value rather than a mock.
 */
private val bundleDir: String
    get() = js("__dirname") as String

private val nodeArch: String
    get() = process.asDynamic().arch as String

/**
 * Runs a resolved plan in the bundled `climat-msh` binary.
 *
 * The plan travels as plain `argv` entries — already delimited by the kernel, so there is no
 * encoding step that could reintroduce quoting bugs. stdio is inherited, so the shell owns the
 * terminal exactly as `/bin/sh` would.
 */
fun spawnMicroshell(plan: ResolvedNode): Promise<Int> {
    val binary =
        microshellBinary()
            ?: throw Exception(
                "`act microsh` is not available on ${process.platform}-$nodeArch. " +
                    "Use `act sh` instead, or open an issue to request this platform.",
            )

    return Promise { resolve, reject ->
        val child =
            NodeChildProcess.spawn(
                binary,
                plan.encode().toTypedArray(),
                jsObjectOf("stdio" to "inherit"),
            )

        child.on("error") { error: dynamic ->
            val message =
                if (error.code == "ENOENT") {
                    "Could not find the microshell binary at `$binary`. The climat install may be incomplete."
                } else {
                    "Failed to start the microshell: ${error.message}"
                }
            reject(Exception(message))
        }

        child.on("exit") { code: Int?, signal: String? ->
            // A null code means the shell itself was signalled (Ctrl-C, say). Statuses of its
            // children are already folded into the code the binary returns.
            resolve(code ?: (128 + signalNumber(signal)))
        }
    }
}

/** `null` when this platform has no bundled binary. */
private fun microshellBinary(): String? {
    val target =
        when (process.platform) {
            "linux" -> if (nodeArch == "x64") "linux-x64" else null
            "darwin" ->
                when (nodeArch) {
                    "x64" -> "darwin-x64"
                    "arm64" -> "darwin-arm64"
                    else -> null
                }
            else -> null
        }

    // The bundle lives in `<pkg>/kotlin/`, the binaries in `<pkg>/msh/`.
    return target?.let { Path.join(bundleDir, "..", "msh", "climat-msh-$it") }
}

private fun signalNumber(signal: String?): Int =
    when (signal) {
        "SIGHUP" -> 1
        "SIGINT" -> 2
        "SIGQUIT" -> 3
        "SIGPIPE" -> 13
        "SIGTERM" -> 15
        else -> 0
    }
