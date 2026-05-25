package com.ember.reader.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ReaderPreferencesDefaultsTest {

    @Test
    fun `publisher styles default off so text alignment is effective`() {
        // Readium ignores textAlign while publisherStyles is true, so the
        // default must be false for the default START alignment to apply.
        assertFalse(ReaderPreferences().publisherStyles)
    }

    @Test
    fun `text align defaults to start (ragged-right)`() {
        assertEquals(TextAlign.START, ReaderPreferences().textAlign)
    }
}
