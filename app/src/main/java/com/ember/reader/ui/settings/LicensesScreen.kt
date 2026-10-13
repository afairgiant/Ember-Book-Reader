package com.ember.reader.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ember.reader.R
import com.ember.reader.ui.settings.components.SettingsScreenHeader
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
fun LicensesScreen(onNavigateBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsScreenHeader(
            overline = stringResource(R.string.licenses_overline),
            title = stringResource(R.string.licenses_screen_title),
            onNavigateBack = onNavigateBack
        )
        LibrariesContainer(modifier = Modifier.fillMaxSize())
    }
}
