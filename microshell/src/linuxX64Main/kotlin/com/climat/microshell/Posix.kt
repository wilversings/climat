@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import climat.msh.posix.msh_close as cClose
import climat.msh.posix.msh_dup2 as cDup2
import climat.msh.posix.msh_enoent as cEnoent
import climat.msh.posix.msh_errno as cErrno
import climat.msh.posix.msh_execvp as cExecvp
import climat.msh.posix.msh_exit as cExit
import climat.msh.posix.msh_fork as cFork
import climat.msh.posix.msh_perror as cPerror
import climat.msh.posix.msh_pipe as cPipe
import climat.msh.posix.msh_setenv as cSetenv
import climat.msh.posix.msh_stdin_fileno as cStdinFileno
import climat.msh.posix.msh_stdout_fileno as cStdoutFileno
import climat.msh.posix.msh_strerror as cStrerror
import climat.msh.posix.msh_waitpid as cWaitpid
import climat.msh.posix.msh_write_stderr as cWriteStderr
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.toKString

internal actual object Posix {
    actual val ENOENT: Int = cEnoent()
    actual val STDIN_FILENO: Int = cStdinFileno()
    actual val STDOUT_FILENO: Int = cStdoutFileno()

    actual fun fork(): Int = cFork()
    actual fun pipe(fds: CValuesRef<IntVar>): Int = cPipe(fds)
    actual fun dup2(oldFd: Int, newFd: Int): Int = cDup2(oldFd, newFd)
    actual fun close(fd: Int): Int = cClose(fd)
    actual fun execvp(file: String, argv: CValuesRef<CPointerVar<ByteVar>>): Int = cExecvp(file, argv)
    actual fun waitpid(pid: Int, status: CValuesRef<IntVar>, options: Int): Int = cWaitpid(pid, status, options)
    actual fun setenv(name: String, value: String, overwrite: Int): Int = cSetenv(name, value, overwrite)

    // The generated binding for the underlying `_exit` wrapper isn't typed `Nothing` (unlike
    // `platform.posix._exit`), even though the C side is annotated `noreturn` — so a trailing throw
    // satisfies the type checker for code that's genuinely unreachable at runtime.
    actual fun exit(status: Int): Nothing {
        cExit(status)
        throw IllegalStateException("unreachable: _exit does not return")
    }

    actual fun errno(): Int = cErrno()
    actual fun strerror(code: Int): String? = cStrerror(code)?.toKString()
    actual fun perror(message: String) {
        cPerror(message)
    }
    actual fun writeStderr(message: String) {
        cWriteStderr(message)
    }
}
