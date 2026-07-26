@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.toKString
import platform.posix.ENOENT as cENOENT
import platform.posix.STDIN_FILENO as cSTDIN_FILENO
import platform.posix.STDOUT_FILENO as cSTDOUT_FILENO
import platform.posix._exit as cExit
import platform.posix.close as cClose
import platform.posix.dup2 as cDup2
import platform.posix.errno as cErrnoVar
import platform.posix.execvp as cExecvp
import platform.posix.fork as cFork
import platform.posix.fputs as cFputs
import platform.posix.perror as cPerror
import platform.posix.pipe as cPipe
import platform.posix.setenv as cSetenv
import platform.posix.stderr as cStderr
import platform.posix.strerror as cStrerror
import platform.posix.waitpid as cWaitpid

internal actual object Posix {
    actual val ENOENT: Int = cENOENT
    actual val STDIN_FILENO: Int = cSTDIN_FILENO
    actual val STDOUT_FILENO: Int = cSTDOUT_FILENO

    actual fun fork(): Int = cFork()
    actual fun pipe(fds: CValuesRef<IntVar>): Int = cPipe(fds)
    actual fun dup2(oldFd: Int, newFd: Int): Int = cDup2(oldFd, newFd)
    actual fun close(fd: Int): Int = cClose(fd)
    actual fun execvp(file: String, argv: CValuesRef<CPointerVar<ByteVar>>): Int = cExecvp(file, argv)
    actual fun waitpid(pid: Int, status: CValuesRef<IntVar>, options: Int): Int = cWaitpid(pid, status, options)
    actual fun setenv(name: String, value: String, overwrite: Int): Int = cSetenv(name, value, overwrite)
    // The commonized klib used for cross-compilation-checking this target from a Linux host
    // doesn't type `_exit` as `Nothing` (unlike the real macOS `platform.posix` klib on a Mac host)
    // — a trailing throw satisfies the type checker either way, for code that's genuinely
    // unreachable at runtime.
    actual fun exit(status: Int): Nothing {
        cExit(status)
        throw IllegalStateException("unreachable: _exit does not return")
    }
    actual fun errno(): Int = cErrnoVar
    actual fun strerror(code: Int): String? = cStrerror(code)?.toKString()
    actual fun perror(message: String) {
        cPerror(message)
    }
    actual fun writeStderr(message: String) {
        cFputs(message, cStderr)
    }
}
