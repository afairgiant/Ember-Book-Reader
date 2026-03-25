package com.ember.reader.ui.reader.common

import com.ember.reader.core.model.ReaderPreferences

/**
 * Maps Ember ReaderPreferences to Readium navigator preferences.
 * TODO: Wire to Readium's EpubPreferences API once exact 3.1.2 types are confirmed.
 * The Readium preferences API varies between versions — this stub prevents
 * compilation failures until integration testing with real books.
 */
object PreferencesMapper {

    fun applyPreferences(preferences: ReaderPreferences) {
        // Will be implemented when Readium navigator preferences API is confirmed.
        // Readium 3.1.2 uses navigator.submitPreferences() but the exact
        // EpubPreferences constructor and Theme enum names need verification.
    }
}
