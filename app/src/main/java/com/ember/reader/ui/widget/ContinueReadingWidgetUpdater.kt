package com.ember.reader.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import com.ember.reader.MainActivity
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/** What the widget shows: the book last read and how far into it the reader is. */
data class WidgetBook(
    val bookId: String,
    val format: BookFormat,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    /** 0 to 100. */
    val percent: Int
) {
    /** The route that opens this book in its reader. */
    val readerRoute: String
        get() = when (format) {
            BookFormat.EPUB -> Routes.epubReader(bookId)
            BookFormat.PDF -> Routes.pdfReader(bookId)
            BookFormat.AUDIOBOOK -> Routes.audiobookReader(bookId)
        }
}

/** The book for the widget: the first of [recentlyReading], with its progress as a whole percent. */
internal fun widgetBookOf(recentlyReading: List<Book>, percentageOf: (bookId: String) -> Float?): WidgetBook? {
    val book = recentlyReading.firstOrNull() ?: return null
    val percent = ((percentageOf(book.id) ?: 0f) * 100).roundToInt().coerceIn(0, 100)
    return WidgetBook(book.id, book.format, book.title, book.author, book.coverUrl, percent)
}

/** Fills in the "continue reading" widgets and keeps them current while the app runs. */
@Singleton
class ContinueReadingWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) {

    private val widgetBook = combine(
        bookRepository.observeRecentlyReading(),
        readingProgressRepository.observeAll()
    ) { books, progress ->
        val percentages = progress.associate { it.bookId to it.percentage }
        widgetBookOf(books, percentages::get)
    }.distinctUntilChanged()

    private val palette = combine(
        appPreferencesRepository.themeModeFlow,
        appPreferencesRepository.oledBlackFlow,
        appPreferencesRepository.accentFlow,
        ::widgetPaletteOf
    ).distinctUntilChanged()

    /**
     * Redraws the widgets whenever the book last read, how far into it, or the app's theme
     * changes.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(widgetBook, palette, ::Pair).collectLatest { (book, palette) ->
                runCatching { draw(book, palette) }.onFailure { Timber.w(it, "Widget: redraw failed") }
            }
        }
    }

    /** Redraws the widgets from what is stored now. */
    suspend fun updateAll() = draw(widgetBook.first(), palette.first())

    private suspend fun draw(book: WidgetBook?, palette: WidgetPalette) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
        if (book == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
            views.setTextViewText(R.id.widget_author, context.getString(R.string.widget_empty_body))
            views.setViewVisibility(R.id.widget_author, View.VISIBLE)
            views.setViewVisibility(R.id.widget_progress_row, View.GONE)
            views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
        } else {
            views.setTextViewText(R.id.widget_title, book.title)
            views.setTextViewText(R.id.widget_author, book.author.orEmpty())
            views.setViewVisibility(R.id.widget_author, if (book.author.isNullOrBlank()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_progress_row, View.VISIBLE)
            views.setProgressBar(R.id.widget_progress, 100, book.percent, false)
            views.setTextViewText(R.id.widget_percent, context.getString(R.string.widget_percent, book.percent))
            val cover = loadCover(book.coverUrl)
            if (cover != null) {
                views.setImageViewBitmap(R.id.widget_cover, cover)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            }
        }
        applyColors(views, palette)
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(book))
        manager.updateAppWidget(ids, views)
    }

    /**
     * Colours the widget from the app's theme. From Android 12 the launcher is given both the
     * light and the dark colours and picks as the device changes. Before that it is given
     * the ones for now, and the progress bar keeps the colours in its drawable, which cannot
     * be changed from here.
     */
    private fun applyColors(views: RemoteViews, palette: WidgetPalette) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val day = palette.day
            val night = palette.night
            views.setColorInt(R.id.widget_background_fill, "setColorFilter", day.background, night.background)
            views.setColorInt(R.id.widget_background_outline, "setColorFilter", day.outline, night.outline)
            views.setColorInt(R.id.widget_title, "setTextColor", day.text, night.text)
            SECONDARY_TEXT_IDS.forEach {
                views.setColorInt(it, "setTextColor", day.textSecondary, night.textSecondary)
            }
            views.setColorStateList(
                R.id.widget_progress,
                "setProgressTintList",
                ColorStateList.valueOf(day.accent),
                ColorStateList.valueOf(night.accent)
            )
            views.setColorStateList(
                R.id.widget_progress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(day.track),
                ColorStateList.valueOf(night.track)
            )
        } else {
            val deviceIsDark = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val colors = palette.forDevice(deviceIsDark)
            views.setInt(R.id.widget_background_fill, "setColorFilter", colors.background)
            views.setInt(R.id.widget_background_outline, "setColorFilter", colors.outline)
            views.setTextColor(R.id.widget_title, colors.text)
            SECONDARY_TEXT_IDS.forEach { views.setTextColor(it, colors.textSecondary) }
        }
    }

    /** Opens the app at [book]'s reader, or just the app when there is no book. */
    private fun openIntent(book: WidgetBook?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            book?.let { putExtra(MainActivity.EXTRA_NAVIGATE_TO, it.readerRoute) }
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The cover through the app's image loader, which knows how to sign in for it. Small, as widgets must be. */
    private suspend fun loadCover(coverUrl: String?): Bitmap? {
        coverUrl ?: return null
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(COVER_WIDTH_PX, COVER_HEIGHT_PX)
            .allowHardware(false)
            .transformations(RoundedCornersTransformation(COVER_CORNER_PX))
            .build()
        return (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }

    private companion object {
        const val REQUEST_OPEN = 7201
        const val COVER_WIDTH_PX = 240
        const val COVER_HEIGHT_PX = 360
        const val COVER_CORNER_PX = 20f
        val SECONDARY_TEXT_IDS = listOf(R.id.widget_label, R.id.widget_author, R.id.widget_percent)
    }
}
