package com.climat

import child_process.ExecSyncOptions
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.domain.action.TemplateActionValue
import com.climat.library.domain.toolchain.Toolchain
import kotlinx.coroutines.await
import process
import vm.createContext
import vm.runInContext

suspend fun exec(
    pathToManifest: String,
    command: Array<String>,
    skipValidation: Boolean = false,
) {
    val manifest = Fs.readFile(untildify(pathToManifest), "utf8").await()
    doExec(manifest, command, skipValidation)
}

fun doExec(
    cliDsl: String,
    command: Array<String>,
    skipValidation: Boolean = true,
) {
    com.climat.library.commandParser.execute(
        command,
        cliDsl,
        { command, toolchain ->
            when (command) {
                is TemplateActionValue -> {
                    child_process.execSync(command.value!!, execSyncOptions())
                }
                is JavaScriptActionValue -> handleCustomScript(command, toolchain)
                else -> throw Exception("${command.type} not supported")
            }
        },
        skipValidation,
    )
}

// `act sh` bodies are POSIX shell: the interpolator wraps user values in single quotes so they
// reach the shell as single literal arguments. Node's execSync runs them through /bin/sh on Unix
// (which strips the quotes) but cmd.exe on Windows (which does not), so the quotes would leak into
// the output. Point execSync at a POSIX sh on Windows to keep behaviour identical across platforms.
private fun execSyncOptions(): ExecSyncOptions {
    val options = jsObjectOf("stdio" to "inherit")
    if (process.platform == "win32") {
        options["shell"] = windowsShell
    }
    return options as ExecSyncOptions
}

// Git for Windows ships a POSIX sh and is a hard requirement for running `sh` actions on Windows.
// Look for its sh.exe next to the `git` already on PATH, then at the default install location, and
// crash with an actionable message if neither exists — a missing shell must fail loudly and early
// rather than silently fall through to cmd.exe (which would mangle the POSIX quoting).
private val windowsShell: String by lazy {
    val candidates = buildList {
        try {
            val gitPath =
                child_process
                    .execSync("where git")
                    .toString("utf8")
                    .lineSequence()
                    .map { it.trim() }
                    .first { it.isNotEmpty() } // e.g. C:\Program Files\Git\cmd\git.exe
            add(Path.win32.join(Path.win32.dirname(Path.win32.dirname(gitPath)), "bin", "sh.exe"))
        } catch (ex: dynamic) {
            // `git` not on PATH; fall back to the default install location below.
        }
        add("C:\\Program Files\\Git\\bin\\sh.exe")
    }

    candidates.firstOrNull { Fs.existsSync(it) }
        ?: throw Exception(
            "Running `sh` actions on Windows requires Git for Windows (it provides the sh.exe that " +
                "climat shells out to), but it could not be found in any of: " +
                candidates.joinToString(", ") + ". Install it from https://git-scm.com/download/win " +
                "and make sure `git` is on your PATH.",
        )
}

fun handleCustomScript(
    command: JavaScriptActionValue,
    toolchain: Toolchain,
) {
    val params: dynamic = object {}
    command.valueForJs?.entries?.forEach {
        params[it.key] = it.value
    }

    val scope =
        jsObjectOf(
            "params" to params,
            "command" to command,
            "toolchain" to toolchain,
            "console" to console,
        )

    runInContext(command.customScript, createContext(scope), "")
}
