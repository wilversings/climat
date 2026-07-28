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

        Fs.ensureDir(climatScriptBin).await()

        // Windows can't execute a bare .js file by name (its extension isn't in
        // PATHEXT), so each command needs a native launcher per shell. Generate
        // the trio npm uses: <name>.cmd (cmd.exe / PowerShell), <name>.ps1
        // (PowerShell) and an extensionless sh script (Git Bash / MSYS).
        writeShimTrio(name, name)
        for (alias in aliases) {
            writeShimTrio(alias, name)
        }

        // TODO: add path automatically
        // This requires writing to the Windows registry
        // Was attempted before but it was deleted (see 608553d9073a08df9e2329c0e3d97354cca4b4e7)

        if (process.env["PATH"]?.contains(climatScriptBin) != true) {
            console.warn(warn("Please add '$climatScriptBin' to system PATH"))
        }
    }

    override suspend fun uninstall(name: String) {
        val commandNames = listOf(name) + getAliases(name)
        Promise
            .all(
                commandNames
                    .flatMap { command -> listOf("$command.cmd", "$command.ps1", command) }
                    .map { fileName -> Fs.rm(platformPath.join(climatScriptBin, fileName), jsObjectOf("force" to true)) }
                    .toTypedArray(),
            ).await()
        removeToolchain(name)
    }

    private suspend fun writeShimTrio(
        fileName: String,
        toolchainName: String,
    ) {
        Promise
            .all(
                arrayOf(
                    Fs.writeFile(platformPath.join(climatScriptBin, "$fileName.cmd"), getCmdShim(toolchainName)),
                    Fs.writeFile(platformPath.join(climatScriptBin, "$fileName.ps1"), getPs1Shim(toolchainName)),
                    Fs.writeFile(platformPath.join(climatScriptBin, fileName), getShShim(toolchainName)),
                ),
            ).await()
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

    private companion object {
        fun getCmdShim(name: String): String = "@echo off${EOL}climat runGlobal \"$name\" %*$EOL"

        fun getPs1Shim(name: String): String = "climat runGlobal \"$name\" @args${EOL}exit \$LASTEXITCODE$EOL"

        fun getShShim(name: String): String = "#!/bin/sh${EOL}climat runGlobal \"$name\" \"\$@\"$EOL"
    }
}
