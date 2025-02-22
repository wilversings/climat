package com.climat

import child_process.ExecSyncOptions
import com.climat.library.commandParser.execute
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.domain.action.TemplateActionValue
import com.climat.library.domain.toolchain.Toolchain
import kotlinx.coroutines.await
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
    execute(
        command,
        cliDsl,
        { command, toolchain ->
            when (command) {
                is TemplateActionValue -> {
                    child_process.execSync(command.value!!, jsObjectOf("stdio" to "inherit") as ExecSyncOptions)
                }
                is JavaScriptActionValue -> handleCustomScript(command, toolchain)
                else -> throw Exception("${command.type} not supported")
            }
        },
        skipValidation,
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
