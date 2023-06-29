package com.simplemobiletools.gallery.pro.utils

import com.simplemobiletools.commons.utils.KietLog
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
fun <T> measureTimeAndLog(id: String? = null, block: () -> T): T {
    val throwable = Throwable()
    val (value, time) = measureTimedValue(block)
    val idString = if (id == null) "" else ", $id"
    val stackTraceElement = if (id == null) throwable.stackTrace[2] else throwable.stackTrace[1]
    KietLog.i("measureTimeAndLog, ${stackTraceElement.fileName}::${stackTraceElement.lineNumber}::${stackTraceElement.methodName}$idString: $time", printStackTrace = false)
    return value
}