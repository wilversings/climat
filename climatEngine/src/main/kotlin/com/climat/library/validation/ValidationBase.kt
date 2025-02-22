package com.climat.library.validation

import com.climat.library.domain.SourceTraceable
import com.climat.library.domain.ref.ParamDefinition
import com.climat.library.domain.ref.Ref
import com.climat.library.domain.refs
import com.climat.library.validation.validations.ValidationCode

internal abstract class ValidationBase {
    internal abstract val type: ValidationResult.ValidationEntryType
    internal abstract val code: ValidationCode
    internal abstract fun validate(ctx: ValidationContext): Sequence<ValidationEntry>

    protected fun getScopeRefs(ctx: ValidationContext): Map<String, List<Ref>> =
        (ctx.traceToRoot.flatMap { it.refs } + ctx.toolchain.refs)
            .groupBy { it.name }

    protected fun getScopeParams(ctx: ValidationContext): Map<String, List<ParamDefinition>> =
        getScopeRefs(ctx)
            .mapValues { it.value.filterIsInstance<ParamDefinition>() }
            .filterValues { it.isNotEmpty() }

    protected fun SourceTraceable.validationEntry(message: String) =
        ValidationEntry(message, sourceMap)
}
