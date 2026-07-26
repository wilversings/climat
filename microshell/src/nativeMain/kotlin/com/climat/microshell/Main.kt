@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import kotlin.system.exitProcess

/**
 * The microshell binary.
 *
 * climat passes the action body as tagged segments through `argv` — literal DSL text plus
 * already-resolved ref values — and the shell language is parsed here, at run time. climat itself
 * has no opinion about shell syntax.
 *
 * Everything below is real POSIX, so pipelines behave exactly as they would under `/bin/sh`,
 * including SIGPIPE and `128 + N` exit statuses.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        Posix.writeStderr("climat-msh: no action body given\n")
        exitProcess(2)
    }

    val command = try {
        parse(decodeSegments(args.toList()))
    } catch (ex: MicroshellParseException) {
        Posix.writeStderr("climat-msh: ${ex.message}\n")
        exitProcess(2)
    }

    exitProcess(execute(command))
}
