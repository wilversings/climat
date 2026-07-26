@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar

/**
 * The small slice of POSIX the executor needs.
 *
 * `linuxX64`'s actual binds a hand-written cinterop with no extra `linkerOpts`, so the binary only
 * needs libc — importing `platform.posix` would drag in Kotlin/Native's bundled klib for that
 * target, which hardcodes `-lresolv -lm -lpthread -lutil -lcrypt -lrt` regardless of which symbols
 * are used. macOS has no equivalent library-availability problem, so its actual uses
 * `platform.posix` directly.
 */
internal expect object Posix {
    val ENOENT: Int
    val STDIN_FILENO: Int
    val STDOUT_FILENO: Int

    fun fork(): Int
    fun pipe(fds: CValuesRef<IntVar>): Int
    fun dup2(oldFd: Int, newFd: Int): Int
    fun close(fd: Int): Int
    fun execvp(file: String, argv: CValuesRef<CPointerVar<ByteVar>>): Int
    fun waitpid(pid: Int, status: CValuesRef<IntVar>, options: Int): Int
    fun setenv(name: String, value: String, overwrite: Int): Int
    fun exit(status: Int): Nothing
    fun errno(): Int
    fun strerror(code: Int): String?
    fun perror(message: String)
    fun writeStderr(message: String)
}
