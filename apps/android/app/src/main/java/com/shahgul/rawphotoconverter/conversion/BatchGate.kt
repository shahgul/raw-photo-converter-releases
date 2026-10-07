package com.shahgul.rawphotoconverter.conversion

import java.util.concurrent.atomic.AtomicReference

class BatchGate {
    private val owner = AtomicReference<Any?>()
    fun tryAcquire(): Any? {
        val lease = Any()
        return if (owner.compareAndSet(null, lease)) lease else null
    }
    fun isOwner(lease: Any): Boolean = owner.get() === lease
    fun release(lease: Any) { owner.compareAndSet(lease, null) }
}
