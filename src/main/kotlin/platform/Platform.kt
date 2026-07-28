package com.climat.platform

import com.climat.Fs
import com.climat.jsObjectOf
import com.climat.library.commandParser.parse
import kotlinx.coroutines.await
import path.path.PlatformPath
import kotlin.js.Promise

abstract class Platform {
    abstract val toolchainHome: String
    abstract val platformPath: PlatformPath

    protected abstract val climatScriptBin: String

    abstract suspend fun install(
        pathToManifest: String,
        name: String,
        aliases: Array<String>,
    )

    abstract suspend fun uninstall(name: String)

    abstract suspend fun purge()

    suspend fun getInstalledToolchains(): List<String> =
        Fs
            .readdir(toolchainHome, jsObjectOf("withFileTypes" to true))
            .await()
            .filter { it.isDirectory() }
            .map { it.name }

    protected suspend fun removeToolchain(name: String) {
        try {
            Fs
                .rm(
                    path = platformPath.join(toolchainHome, name),
                    options = jsObjectOf("recursive" to true),
                ).await()
        } catch (ex: dynamic) {
            if (ex.code == "ENOENT") {
                throw Exception("Toolchain named `$name` was not found")
            }
            throw ex
        }
    }

    protected suspend fun getAliases(name: String): List<String> =
        parse(
            Fs
                .readFile(
                    path = platformPath.join(toolchainHome, name, MAIN_MANIFEST_NAME),
                    options = "utf8",
                ).await()
                .toString(),
        ).aliases.map { it.name }

    protected suspend fun removeAliasSymlinks(
        name: String,
        nameSuffix: String = "",
    ) {
        Promise
            .all(
                getAliases(name)
                    .map { alias -> Fs.unlink(platformPath.join(climatScriptBin, alias + nameSuffix)) }
                    .toTypedArray(),
            ).await()
    }

    protected suspend fun moveManifestToClimatHome(
        toolchainHome: String,
        manifest: String,
        name: String,
    ) {
        val toolchainDir = platformPath.join(toolchainHome, name)

        Fs.ensureDir(path = toolchainHome).await()
        Fs.chmod(path = toolchainHome, mode = FILE_PERMISSIONS).await()

        Fs.ensureDir(path = toolchainDir).await()
        Fs.chmod(path = toolchainDir, mode = FILE_PERMISSIONS).await()

        Fs
            .writeFile(
                path = platformPath.join(toolchainDir, MAIN_MANIFEST_NAME),
                data = manifest,
            ).await()
    }
}
