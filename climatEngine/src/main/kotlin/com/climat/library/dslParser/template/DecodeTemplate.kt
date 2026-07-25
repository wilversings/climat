package com.climat.library.dslParser.template

import climat.lang.DslParser.ActionTemplateEntryContext
import climat.lang.DslParser.MappingContext
import climat.lang.DslParser.StringTemplateEntryContext
import com.climat.library.domain.action.template.Interpolation
import com.climat.library.domain.action.template.Mapping
import com.climat.library.domain.action.template.SimpleString
import com.climat.library.domain.action.template.Template
import com.climat.library.dslParser.exception.assertRequire
import com.climat.library.dslParser.exception.throwUnexpected
import com.climat.library.utils.unescape

// The `{}` placeholder becomes a `null` piece; everything else is literal text (with `\"` and `\{`
// unescaped), so `@{x ? "--today-is={}"}` decodes to `["--today-is=", null]`.
private fun decodeMapping(mapping: MappingContext?): Mapping? =
    mapping?.mappingTemplateEntry()
        ?.map { entry ->
            if (entry.MappingTemplate_PLACEHOLDER() != null) {
                null
            } else {
                entry.text.unescape('"').unescape('{')
            }
        }
        ?.let(::Mapping)

internal fun decodeTemplate(cliDsl: String, templateEntries: List<ActionTemplateEntryContext>): Template =
    templateEntries.map { entry ->
        val actionTemplateContent = entry.actionTemplateContent()
        val actionTemplateInterpolation = entry.actionTemplateInterpolation()

        when {
            actionTemplateContent != null -> SimpleString(actionTemplateContent.text.unescape('@').unescape("%>"))

            actionTemplateInterpolation != null -> Interpolation(
                name = actionTemplateInterpolation.assertRequire(cliDsl) { Interpolation_IDENTIFIER() }.text,
                mapping = decodeMapping(actionTemplateInterpolation.mapping()),
                isFlipped = actionTemplateInterpolation.Interpolation_NEGATE() != null
            )

            else -> entry.throwUnexpected("No content or interpolation found", cliDsl)
        }
    }.let(::Template)

internal fun decodeTemplate(cliDsl: String, templateEntries: List<StringTemplateEntryContext>): Template =
    templateEntries.map { entry ->
        val stringTemplateContent = entry.stringTemplateContent()
        val stringTemplateInterpolation = entry.stringTemplateInterpolation()

        when {
            stringTemplateContent != null -> SimpleString(stringTemplateContent.text.unescape('@').unescape('"'))

            stringTemplateInterpolation != null -> Interpolation(
                name = stringTemplateInterpolation.assertRequire(cliDsl) { Interpolation_IDENTIFIER() }.text,
                mapping = decodeMapping(stringTemplateInterpolation.mapping()),
                isFlipped = stringTemplateInterpolation.Interpolation_NEGATE() != null
            )

            else -> entry.throwUnexpected("No content or interpolation found", cliDsl)
        }
    }.let(::Template)
