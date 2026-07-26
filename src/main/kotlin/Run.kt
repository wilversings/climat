package com.climat

import child_process.ExecSyncOptions
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.domain.action.MicroshellActionValue
import com.climat.library.domain.action.TemplateActionValue
import com.climat.library.domain.toolchain.Toolchain
import com.climat.microshell.spawnMicroshell
import kotlinx.coroutines.await
import process
import vm.createContext
import vm.runInContext
import kotlin.js.Promise

suspend fun exec(
    pathToManifest: String,
    command: Array<String>,
    skipValidation: Boolean = false,
) {
    val manifest = Fs.readFile(untildify(pathToManifest), "utf8").await()
    doExec(manifest, command, skipValidation)
}

suspend fun doExec(
    cliDsl: String,
    command: Array<String>,
    skipValidation: Boolean = true,
) {
    // `handleMatch` invokes this handler exactly once, as the last thing it does, so the microshell
    // promise can simply be captured and awaited afterwards — no change to the engine's exported
    // `(Action, Toolchain) -> Unit` signature is needed.
    var pending: Promise<Int>? = null

    com.climat.library.commandParser.execute(
        command,
        cliDsl,
        { command, toolchain ->
            when (command) {
                is TemplateActionValue -> {
                    child_process.execSync(command.value!!, jsObjectOf("stdio" to "inherit") as ExecSyncOptions)
                }
                is MicroshellActionValue -> pending = spawnMicroshell(command.plan!!)
                is JavaScriptActionValue -> handleCustomScript(command, toolchain)
                else -> throw Exception("${command.type} not supported")
            }
        },
        skipValidation,
    )

    // Awaiting (rather than firing and forgetting) keeps failures on the existing error path: the
    // promise rejects, and AsyncClimatCli's continuation reports it.
    pending?.await()?.let { status ->
        // `process.exitCode`, never `process.exit()` — the latter truncates pending writes on
        // inherited stdio.
        if (status != 0) process.exitCode = status
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
