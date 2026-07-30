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

// Git for Windows ships a POSIX sh and is the de-facto prerequisite for POSIX shell tooling there.
// Locate it next to the `git` already on PATH, falling back to the default install location.
private val windowsShell: String by lazy {
    try {
        val gitPath =
            child_process
                .execSync("where git")
                .toString("utf8")
                .lineSequence()
                .map { it.trim() }
                .first { it.isNotEmpty() } // e.g. C:\Program Files\Git\cmd\git.exe
        Path.win32.join(Path.win32.dirname(Path.win32.dirname(gitPath)), "bin", "sh.exe")
    } catch (ex: dynamic) {
        "C:\\Program Files\\Git\\bin\\sh.exe"
    }
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
