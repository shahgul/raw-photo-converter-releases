package com.shahgul.rawphotoconverter.conversion

internal object RetryPolicy {
    fun failed(results: List<ConversionResult>, sourceUri: String? = null): List<ConversionResult> =
        results.filter { result ->
            result.error != null && result.sourceUri != null &&
                (sourceUri == null || result.sourceUri == sourceUri)
        }

    fun merge(previous: List<ConversionResult>, retried: List<ConversionResult>): List<ConversionResult> {
        val bySource = retried.mapNotNull { result -> result.sourceUri?.let { it to result } }.toMap()
        return previous.map { result ->
            if (result.error == null) result else result.sourceUri?.let(bySource::get) ?: result
        }
    }
}
