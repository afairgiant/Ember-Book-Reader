package com.ember.reader.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import com.ember.reader.MainActivity
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
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
    private val readingProgressRepository: ReadingProgressRepository
) {

    private val widgetBook = combine(
        bookRepository.observeRecentlyReading(),
        readingProgressRepository.observeAll()
    ) { books, progress ->
        val percentages = progress.associate { it.bookId to it.percentage }
        widgetBookOf(books, percentages::get)
    }.distinctUntilChanged()

    /** Redraws the widgets whenever the book last read, or how far into it, changes. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            widgetBook.collectLatest { book ->
                runCatching { draw(book) }.onFailure { Timber.w(it, "Widget: redraw failed") }
            }
        }
    }

    /** Redraws the widgets from what is stored now. */
    suspend fun updateAll() = draw(widgetBook.first())

    private suspend fun draw(book: WidgetBook?) {
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
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(book))
        manager.updateAppWidget(ids, views)
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
    }
}
