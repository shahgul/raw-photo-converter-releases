package com.shahgul.rawphotoconverter.conversion

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test fun selectsOnlyFailedFilesWithRetryableSourceUris() {
        val results = listOf(
            ConversionResult("saved.ARW", sourceUri = "content://photos/saved", sizeBytes = 200_000),
            ConversionResult("existing.CR3", sourceUri = "content://photos/existing", skipped = true),
            ConversionResult("failed.CR3", sourceUri = "content://photos/failed", error = "Bad metadata"),
            ConversionResult("unavailable.CR2", error = "Source permission expired"),
        )

        assertEquals(
            listOf("content://photos/failed"),
            RetryPolicy.failed(results).mapNotNull { it.sourceUri },
        )
    }

    @Test fun singleFileRetrySelectsOnlyTheRequestedFailure() {
        val results = listOf(
            ConversionResult("first.CR3", sourceUri = "content://photos/first", error = "Decode failed"),
            ConversionResult("second.CR3", sourceUri = "content://photos/second", error = "Read failed"),
        )

        assertEquals(
            listOf("content://photos/second"),
            RetryPolicy.failed(results, "content://photos/second").mapNotNull { it.sourceUri },
        )
    }

    @Test fun mergeReplacesRetriedFailuresAndKeepsOtherResults() {
        val saved = ConversionResult("saved.ARW", sourceUri = "content://photos/saved", sizeBytes = 200_000)
        val skipped = ConversionResult("existing.CR3", sourceUri = "content://photos/existing", skipped = true)
        val failed = ConversionResult("retry.CR3", sourceUri = "content://photos/retry", error = "First attempt failed")
        val stillFailed = ConversionResult("later.CR2", sourceUri = "content://photos/later", error = "Read failed")
        val retried = ConversionResult("retry.CR3", sourceUri = "content://photos/retry", sizeBytes = 250_000)
        val unexpectedSavedRetry = ConversionResult("saved.ARW", sourceUri = "content://photos/saved", error = "Must not replace success")

        assertEquals(
            listOf(saved, skipped, retried, stillFailed),
            RetryPolicy.merge(listOf(saved, skipped, failed, stillFailed), listOf(retried, unexpectedSavedRetry)),
        )
    }

    @Test fun retryProgressUsesAttemptResultsAndKeepsPriorResultsForReview() {
        val prior = ConversionResult("failed.CR3", sourceUri = "content://photos/failed", error = "First attempt failed")
        val attempt = ConversionResult("failed.CR3", sourceUri = "content://photos/failed", sizeBytes = 250_000)
        val state = BatchState(
            running = true,
            total = 1,
            results = listOf(prior),
            retrying = true,
            attemptResults = listOf(attempt),
        )

        assertEquals(listOf(attempt), state.processedResults)
        assertEquals(listOf(prior), state.results)
    }
}
