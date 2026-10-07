package com.shahgul.rawphotoconverter.conversion

import org.junit.Assert.*
import org.junit.Test

class BatchGateTest {
    @Test fun retryCannotEnterBeforePreviousWorkerCleanupFinishes() {
        val gate = BatchGate()
        val old = requireNotNull(gate.tryAcquire())
        assertNull(gate.tryAcquire())
        gate.release(old)
        val next = requireNotNull(gate.tryAcquire())
        assertFalse(gate.isOwner(old))
        assertTrue(gate.isOwner(next))
        gate.release(old) // A late old-service callback cannot release the new batch.
        assertNull(gate.tryAcquire())
        gate.release(next)
        assertNotNull(gate.tryAcquire())
    }
}
