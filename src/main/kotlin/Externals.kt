package com.climat

import fs.Dirent
import path.path.PlatformPath
import kotlin.js.Promise

external interface Response {
    val ok: Boolean
    val status: String
    val statusText: String

    fun text(): Promise<String>
}

@JsModule("node-fetch")
@JsNonModule
external fun fetch(uri: String): Promise<Response>

@JsModule("untildify")
@JsNonModule
external fun untildify(str: String): String

@JsModule("fs-extra")
@JsNonModule
external object Fs {
    fun readFile(
        path: String,
        options: String,
    ): Promise<String>

    fun ensureDir(path: String): Promise<Unit>

    fun writeFile(
        path: String,
        data: String,
    ): Promise<Unit>

    fun writeFile(
        a: String,
        b: String,
        options: dynamic,
    ): Promise<Unit>

    fun chmod(
        path: String,
        mode: Number,
    ): Promise<Unit>

    fun rm(
        path: String,
        options: dynamic,
    ): Promise<Unit>

    fun rm(path: String): Promise<Unit>

    fun unlink(path: String): Promise<Unit>

    fun pathExists(path: String): Promise<Boolean>

    fun existsSync(path: String): Boolean

    fun symlink(
        target: String,
        path: String,
    ): Promise<Unit>

    fun readdir(
        path: String,
        options: dynamic,
    ): Promise<Array<Dirent>>
}

@JsModule("path")
@JsNonModule
external object Path {
    val win32: PlatformPath
    val posix: PlatformPath

    fun join(vararg paths: String): String
}

@JsModule("yesno")
@JsNonModule
external fun yesno(options: dynamic): Promise<Boolean>
