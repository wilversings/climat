package com.climat.microshell

import com.climat.Path
import com.climat.jsObjectOf
import com.climat.library.domain.action.ActionSegment
import com.climat.library.domain.action.LiteralText
import com.climat.library.domain.action.ResolvedValue
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
 * Runs a resolved action body in the bundled `climat-msh` binary, which parses the shell language
 * itself.
 *
 * This is the only place that knows both vocabularies: it maps the engine's [ActionSegment]s onto
 * the microshell's [Segment]s. The body travels as plain `argv` entries — already delimited by the
 * kernel, so there is no encoding step that could reintroduce quoting bugs. stdio is inherited, so
 * the shell owns the terminal exactly as `/bin/sh` would.
 */
fun spawnMicroshell(body: List<ActionSegment>): Promise<Int> {
    val segments =
        body.map { segment ->
            when (segment) {
                is LiteralText -> Literal(segment.text)
                is ResolvedValue -> Value(segment.value)
            }
        }
    return spawnWith(segments.encodeToArgv())
}

private fun spawnWith(argv: List<String>): Promise<Int> {
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
                argv.toTypedArray(),
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
