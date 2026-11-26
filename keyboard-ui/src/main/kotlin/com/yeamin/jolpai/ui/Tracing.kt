package com.yeamin.jolpai.ui

import android.os.Trace
import androidx.core.os.TraceCompat

/**
 * Named sections for system traces (Perfetto, Macrobenchmark in `:benchmark`). When nobody is
 * tracing, each call is a single flag check, so these stay in release builds. Section names start
 * with "Jolpai." and are read by the benchmarks; rename them in both places.
 */
internal inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}

/** A section that may end on another thread (coroutines that hop threads). */
internal fun beginAsyncTrace(name: String, cookie: Int) = TraceCompat.beginAsyncSection(name, cookie)

internal fun endAsyncTrace(name: String, cookie: Int) = TraceCompat.endAsyncSection(name, cookie)
