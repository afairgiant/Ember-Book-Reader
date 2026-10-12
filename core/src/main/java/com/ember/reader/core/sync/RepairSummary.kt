package com.ember.reader.core.sync

/**
 * How a repair of highlights or bookmarks went.
 *
 * @param unresolved items that still have no real CFI, because their place in the book
 * could not be worked out or the server did not take the change.
 */
data class RepairSummary(val repaired: Int, val unresolved: Int)
