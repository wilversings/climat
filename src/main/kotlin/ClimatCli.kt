package com.climat

import com.climat.Path.join
import com.climat.library.commandParser.parse
import com.climat.platform.MAIN_MANIFEST_NAME
import com.climat.platform.Platform
import com.climat.platform.Unix
import com.climat.platform.Windows
import kotlinx.coroutines.await
import os.EOL
import process

class ClimatCli {
    private var platform: Platform =
        when (process.platform) {
            "linux", "darwin" -> Unix()
            "win32" -> Windows()
            else -> throw Exception("${process.platform} OS is not supported")
        }

    suspend fun install(uriToDsl: String) {
        val dslText = getDslText(uriToDsl)
        val toolchain = parse(dslText)
        val aliasStrings = toolchain.aliases.map { it.name }.toTypedArray()

        platform.install(dslText, toolchain.name, aliasStrings)
    }

    suspend fun runGlobal(
        name: String,
        command: Array<String>,
    ) {
        val manifest = Fs.readFile(platform.platformPath.join(platform.toolchainHome, name, MAIN_MANIFEST_NAME), "utf8").await()
        doExec(manifest, command, true)
    }

    suspend fun uninstall(name: String) = platform.uninstall(name)

    suspend fun run(command: Array<String>) {
        var wd = process.cwd()
        var i = 0

        while (Fs.pathExists(wd).await() && i < 50) {
            val pathToManifest = join(wd, MAIN_MANIFEST_NAME)
            if (Fs.pathExists(pathToManifest).await()) {
                exec(pathToManifest, command)
                return
            }

            wd = join(wd, "..")
            ++i
        }

        console.log("No $MAIN_MANIFEST_NAME found up the directory hierarchy")
    }

    suspend fun list() {
        platform.getInstalledToolchains().forEach { console.log(it) }
    }

    suspend fun purge() {
        val ok =
            yesno(
                jsObjectOf(
                    "question" to "Are you sure you want to delete these toolchains? (y/n)$EOL${
                        platform.getInstalledToolchains().joinToString(EOL)
                    }$EOL>",
                    "defaultValue" to null,
                ),
            ).await()
        if (ok) {
            platform.purge()
        }
    }

    private companion object {
        suspend fun getDslText(uriToDsl: String): String {
            val isHttpUri = uriToDsl.startsWith("https://") || uriToDsl.startsWith("http://")

            return if (isHttpUri) {
                val response = fetch(uriToDsl).await()
                if (response.ok) {
                    response.text().await()
                } else {
                    throw Exception("Server response ${response.status}: ${response.statusText}")
                }
            } else {
                Fs.readFile(untildify(uriToDsl), "utf8").await()
            }
        }
    }
}
