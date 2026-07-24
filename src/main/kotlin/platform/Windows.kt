package com.climat.platform

import NodeJS.get
import com.climat.Fs
import com.climat.Path
import com.climat.jsObjectOf
import com.climat.output.warn
import kotlinx.coroutines.await
import os.EOL
import process
import kotlin.js.Promise

class Windows : Platform() {
    override val toolchainHome = WINDOWS_TOOLCHAIN_HOME
    override val platformPath = Path.win32
    override val climatScriptBin = WINDOWS_CLIMAT_BIN_PATH

    override suspend fun install(
        pathToManifest: String,
        name: String,
        aliases: Array<String>,
    ) {
        moveManifestToClimatHome(toolchainHome, pathToManifest, name)

        val batchFilePath = getBatchFilePath(name)
        Fs.ensureDir(climatScriptBin).await()

        Fs.writeFile(batchFilePath, getBatchScript(name)).await()

        val aliasScript = getAliasScript(name)

        Promise
            .all(
                aliases.map { Fs.writeFile(getBatchFilePath(it), aliasScript) }.toTypedArray(),
            ).await()

        // TODO: add path automatically
        // This requires writing to the Windows registry
        // Was attempted before but it was deleted (see 608553d9073a08df9e2329c0e3d97354cca4b4e7)

        if (process.env["PATH"]?.contains(climatScriptBin) != true) {
            console.warn(warn("Please add '$climatScriptBin' to system PATH"))
        }
    }

    override suspend fun uninstall(name: String) {
        removeAliasSymlinks(name, ".bat")
        removeToolchain(name)
        Fs.rm(getBatchFilePath(name)).await()
    }

    override suspend fun purge() {
        Fs
            .rm(
                WINDOWS_CLIMAT_HOME,
                jsObjectOf(
                    "recursive" to true,
                ),
            ).await()
    }

    private fun getBatchFilePath(name: String): String = platformPath.join(climatScriptBin, "$name.bat")

    private companion object {
        fun getBatchScript(name: String): String = "@echo off${EOL}climat runGlobal \"$name\" %*"

        fun getAliasScript(name: String): String = "@echo off$EOL%~dp0$name %*"
    }
}
