@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import platform.posix.fputs
import platform.posix.stderr
import kotlin.system.exitProcess

/**
 * The microshell binary. climat resolves an action into a plan and passes it straight through
 * `argv`; everything below here is real POSIX, so pipelines behave exactly as they would under
 * `/bin/sh` — including SIGPIPE and `128 + N` exit statuses.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        fputs("climat-msh: no plan given\n", stderr)
        exitProcess(2)
    }

    val plan = try {
        decodePlan(args.toList())
    } catch (ex: MicroshellParseException) {
        fputs("climat-msh: ${ex.message}\n", stderr)
        exitProcess(2)
    }

    exitProcess(execute(plan))
}
