package com.ember.reader.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The "continue reading" home-screen widget. Android calls this when a widget is added and
 * every half hour after; while the app is running [ContinueReadingWidgetUpdater] keeps the
 * widget current without waiting for that.
 */
class ContinueReadingWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun continueReadingWidgetUpdater(): ContinueReadingWidgetUpdater
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val updater = EntryPointAccessors
            .fromApplication(context.applicationContext, Dependencies::class.java)
            .continueReadingWidgetUpdater()
        // The broadcast ends when this returns; goAsync keeps it open for the lookup.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updater.updateAll()
            } catch (e: Exception) {
                Timber.w(e, "Widget: update failed")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Whether this launcher lets an app offer its widget, which not all do. */
        fun canPin(context: Context): Boolean =
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

        /** Asks the launcher to add the widget; it shows its own confirmation. */
        fun requestPin(context: Context) {
            AppWidgetManager.getInstance(context).requestPinAppWidget(
                ComponentName(context, ContinueReadingWidget::class.java),
                null,
                null
            )
        }
    }
}
