package com.tvapp.livetv.web

import java.util.concurrent.atomic.AtomicBoolean

internal class IptvOperationGate {
    private val acquired = AtomicBoolean(false)
    val isBusy: Boolean get() = acquired.get()

    fun tryAcquire(): Boolean = acquired.compareAndSet(false, true)
    fun release() { acquired.set(false) }
}
