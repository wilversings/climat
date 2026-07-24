package com.climat.output

import org.lighthousegames.logging.LogLevelController
import org.lighthousegames.logging.Logger
import org.lighthousegames.logging.PlatformLogger
import org.lighthousegames.logging.TagProvider
import kotlin.js.Date

class ColoredLogger(
    private val logLevel: LogLevelController,
) : Logger,
    TagProvider,
    LogLevelController by logLevel {
    private val decoratee = PlatformLogger(logLevel)

    override fun debug(
        tag: String,
        msg: String,
    ) {
        console.log(debug(preface("D", tag)), debug(msg))
    }

    override fun error(
        tag: String,
        msg: String,
        t: Throwable?,
    ) {
        decoratee.error(tag, msg, t)
    }

    override fun info(
        tag: String,
        msg: String,
    ) {
        decoratee.info(tag, msg)
    }

    override fun verbose(
        tag: String,
        msg: String,
    ) {
        decoratee.verbose(tag, msg)
    }

    override fun warn(
        tag: String,
        msg: String,
        t: Throwable?,
    ) {
        decoratee.warn(tag, msg, t)
    }

    override fun createTag(fromClass: String?): Pair<String, String> = decoratee.createTag(fromClass)

    companion object {
        private fun d2(i: Int): String = if (i < 10) "0$i" else i.toString()

        private fun d3(i: Int): String = if (i < 100) ("0" + if (i < 10) "0" else "") + i else i.toString()

        private fun preface(
            level: String,
            tag: String,
        ): String {
            val d = Date()
            val timestamp =
                "${d.getMonth() + 1}/${d.getDate()} ${d2(d.getHours())}:${d2(d.getMinutes())}:${d2(d.getSeconds())}.${
                    d3(d.getMilliseconds())
                }"
            val str = if (tag.isEmpty()) "$level:" else "$level/$tag:"
            return "$timestamp $str"
        }
    }
}
