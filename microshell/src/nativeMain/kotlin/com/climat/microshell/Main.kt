@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import platform.posix.fputs
import platform.posix.stderr
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
        fputs("climat-msh: no action body given\n", stderr)
        exitProcess(2)
    }

    val command = try {
        parse(decodeSegments(args.toList()))
    } catch (ex: MicroshellParseException) {
        fputs("climat-msh: ${ex.message}\n", stderr)
        exitProcess(2)
    }

    exitProcess(execute(command))
}
