@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.climat.microshell

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.ENOENT
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix._exit
import platform.posix.close
import platform.posix.dup2
import platform.posix.errno
import platform.posix.execvp
import platform.posix.fork
import platform.posix.fputs
import platform.posix.perror
import platform.posix.pipe
import platform.posix.setenv
import platform.posix.stderr
import platform.posix.strerror
import platform.posix.waitpid

internal fun execute(node: ResolvedNode): Int = when (node) {
    is RSeq -> node.items.fold(0) { _, item -> execute(item) }

    is RAndOr -> {
        val left = execute(node.left)
        when (node.op) {
            Op.And -> if (left == 0) execute(node.right) else left
            Op.Or -> if (left != 0) execute(node.right) else left
        }
    }

    is RPipeline -> runPipeline(node.stages)
    is RCommand -> runCommand(node)
}

private fun runCommand(command: RCommand): Int {
    val pid = fork()
    if (pid < 0) {
        perror("climat-msh: fork")
        return 1
    }
    if (pid == 0) {
        execCommand(command)
        _exit(127) // unreachable: execCommand always terminates the child
    }
    return awaitStatus(pid)
}

/**
 * Runs the stages connected by real `pipe(2)`s.
 *
 * The parent closes **both** ends of every pipe as soon as the children hold them, and each child
 * closes the ends it did not dup. That is what makes an early-exiting consumer deliver SIGPIPE to
 * its producer — leave any copy open and `yes | head -3` never terminates.
 */
private fun runPipeline(stages: List<ResolvedNode>): Int {
    if (stages.size == 1) return execute(stages.single())

    val pids = IntArray(stages.size)
    var prevRead = -1

    for (index in stages.indices) {
        val isLast = index == stages.lastIndex
        var readEnd = -1
        var writeEnd = -1

        if (!isLast) {
            memScoped {
                val fds = allocArray<IntVar>(2)
                if (pipe(fds) != 0) {
                    perror("climat-msh: pipe")
                    return 1
                }
                readEnd = fds[0]
                writeEnd = fds[1]
            }
        }

        val pid = fork()
        if (pid < 0) {
            perror("climat-msh: fork")
            return 1
        }

        if (pid == 0) {
            if (prevRead >= 0) {
                dup2(prevRead, STDIN_FILENO)
                close(prevRead)
            }
            if (!isLast) {
                dup2(writeEnd, STDOUT_FILENO)
                close(writeEnd)
                // Critical: an extra reader held here would keep the *next* pipe alive and stop
                // the downstream consumer's exit from ever signalling this producer.
                close(readEnd)
            }
            when (val stage = stages[index]) {
                is RCommand -> execCommand(stage)
                else -> _exit(execute(stage))
            }
            _exit(127) // unreachable
        }

        pids[index] = pid
        if (prevRead >= 0) close(prevRead)
        if (!isLast) {
            close(writeEnd)
            prevRead = readEnd
        }
    }

    // A pipeline reports its last stage, but every child must be reaped.
    var status = 0
    for (pid in pids) status = awaitStatus(pid)
    return status
}

/** Replaces the calling (child) process. Only ever returns by terminating it. */
private fun execCommand(command: RCommand) {
    memScoped {
        command.assignments.forEach { (name, value) -> setenv(name, value, 1) }
        val argv = allocArray<CPointerVar<ByteVar>>(command.argv.size + 1)
        command.argv.forEachIndexed { i, arg -> argv[i] = arg.cstr.getPointer(this) }
        argv[command.argv.size] = null
        execvp(command.argv[0], argv)
    }

    val code = errno
    val reason = strerror(code)?.toKString() ?: "exec failed"
    fputs("climat-msh: ${command.argv[0]}: $reason\n", stderr)
    _exit(if (code == ENOENT) 127 else 126)
}

private fun awaitStatus(pid: Int): Int = memScoped {
    val slot = alloc<IntVar>()
    if (waitpid(pid, slot.ptr, 0) < 0) return@memScoped 1
    val status = slot.value
    val signal = status and 0x7f
    // Mirror the shell convention: 128 + N for a signalled child.
    if (signal != 0 && signal != 0x7f) 128 + signal else (status shr 8) and 0xff
}
