package com.climat.library.validation

import com.climat.library.domain.toolchain.Toolchain

internal class ValidationContext(
    val toolchain: Toolchain,
    val traceToRoot: List<Toolchain>
)
