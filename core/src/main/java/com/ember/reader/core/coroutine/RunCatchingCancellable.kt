package com.ember.reader.core.coroutine

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] so that coroutine cancellation
 * propagates instead of being swallowed into [Result.failure]. Always prefer this over
 * raw `runCatching` when the block contains suspend calls.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
