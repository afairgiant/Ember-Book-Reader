package com.ember.reader.ui.editmetadata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.FetchMetadataRequest
import com.ember.reader.core.grimmory.GrimmoryBookMetadata
import com.ember.reader.core.grimmory.MetadataClearFlags
import com.ember.reader.core.grimmory.MetadataClient
import com.ember.reader.core.grimmory.MetadataProvider
import com.ember.reader.core.grimmory.MetadataReplaceMode
import com.ember.reader.core.grimmory.MetadataSearchEvent
import com.ember.reader.core.grimmory.MetadataUpdateWrapper
import com.ember.reader.core.grimmory.searchableProviders
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.ui.common.friendlyErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private fun String.parseCommaSeparated(): List<String>? =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null }

/** Fields Ember exposes for editing. Keep in sync with [EditableMetadata]. */
enum class MetadataFieldKey {
    // Basic
    Title, Subtitle, Authors, Publisher, PublishedDate, Description,
    // Series
    SeriesName, SeriesNumber, SeriesTotal,
    // Book details
    Language, Isbn13, Isbn10, PageCount,
    // Arrays
    Categories, Moods, Tags,
    // Audiobook
    Narrator, Abridged,
    // Content rating
    AgeRating, ContentRating,
    // Provider IDs — common
    Asin, GoodreadsId, GoogleId, HardcoverId, HardcoverBookId, ExternalUrl,
    // Provider ratings — common
    AmazonRating, AmazonReviewCount,
    GoodreadsRating, GoodreadsReviewCount,
    HardcoverRating, HardcoverReviewCount,
    // Provider IDs — niche (bottom of form)
    ComicvineId, DoubanId, LubimyczytacId, RanobedbId, AudibleId,
    // Provider ratings — niche
    DoubanRating, DoubanReviewCount,
    LubimyczytacRating, RanobedbRating,
    AudibleRating, AudibleReviewCount,
}

/**
 * Form-friendly (all-string) projection of [GrimmoryBookMetadata] for the editor UI.
 * All values stored as strings; numeric/boolean fields are parsed on save.
 */
data class EditableMetadata(private val fields: Map<MetadataFieldKey, String> = emptyMap()) {
    fun get(key: MetadataFieldKey): String = fields[key].orEmpty()
    fun set(key: MetadataFieldKey, value: String): EditableMetadata =
        EditableMetadata(fields + (key to value))

    companion object {
        private fun fmtFloat(v: Float?): String =
            v?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }.orEmpty()
        private fun fmtDouble(v: Double?): String = v?.toString().orEmpty()
        private fun fmtInt(v: Int?): String = v?.toString().orEmpty()
        private fun fmtBool(v: Boolean?): String = v?.toString().orEmpty()
        private fun fmtSet(v: Set<String>?): String = v?.joinToString(", ").orEmpty()

        fun from(m: GrimmoryBookMetadata): EditableMetadata = EditableMetadata(
            buildMap {
                put(MetadataFieldKey.Title, m.title.orEmpty())
                put(MetadataFieldKey.Subtitle, m.subtitle.orEmpty())
                put(MetadataFieldKey.Authors, m.authors?.joinToString(", ").orEmpty())
                put(MetadataFieldKey.Publisher, m.publisher.orEmpty())
                put(MetadataFieldKey.PublishedDate, m.publishedDate.orEmpty())
                put(MetadataFieldKey.Description, m.description.orEmpty())
                put(MetadataFieldKey.SeriesName, m.seriesName.orEmpty())
                put(MetadataFieldKey.SeriesNumber, fmtFloat(m.seriesNumber))
                put(MetadataFieldKey.SeriesTotal, fmtInt(m.seriesTotal))
                put(MetadataFieldKey.Language, m.language.orEmpty())
                put(MetadataFieldKey.Isbn13, m.isbn13.orEmpty())
                put(MetadataFieldKey.Isbn10, m.isbn10.orEmpty())
                put(MetadataFieldKey.PageCount, fmtInt(m.pageCount))
                put(MetadataFieldKey.Categories, fmtSet(m.categories))
                put(MetadataFieldKey.Moods, fmtSet(m.moods))
                put(MetadataFieldKey.Tags, fmtSet(m.tags))
                put(MetadataFieldKey.Narrator, m.narrator.orEmpty())
                put(MetadataFieldKey.Abridged, fmtBool(m.abridged))
                put(MetadataFieldKey.AgeRating, fmtInt(m.ageRating))
                put(MetadataFieldKey.ContentRating, m.contentRating.orEmpty())
                put(MetadataFieldKey.Asin, m.asin.orEmpty())
                put(MetadataFieldKey.GoodreadsId, m.goodreadsId.orEmpty())
                put(MetadataFieldKey.GoogleId, m.googleId.orEmpty())
                put(MetadataFieldKey.HardcoverId, m.hardcoverId.orEmpty())
                put(MetadataFieldKey.HardcoverBookId, m.hardcoverBookId.orEmpty())
                put(MetadataFieldKey.ExternalUrl, m.externalUrl.orEmpty())
                put(MetadataFieldKey.AmazonRating, fmtDouble(m.amazonRating))
                put(MetadataFieldKey.AmazonReviewCount, fmtInt(m.amazonReviewCount))
                put(MetadataFieldKey.GoodreadsRating, fmtDouble(m.goodreadsRating))
                put(MetadataFieldKey.GoodreadsReviewCount, fmtInt(m.goodreadsReviewCount))
                put(MetadataFieldKey.HardcoverRating, fmtDouble(m.hardcoverRating))
                put(MetadataFieldKey.HardcoverReviewCount, fmtInt(m.hardcoverReviewCount))
                put(MetadataFieldKey.ComicvineId, m.comicvineId.orEmpty())
                put(MetadataFieldKey.DoubanId, m.doubanId.orEmpty())
                put(MetadataFieldKey.LubimyczytacId, m.lubimyczytacId.orEmpty())
                put(MetadataFieldKey.RanobedbId, m.ranobedbId.orEmpty())
                put(MetadataFieldKey.AudibleId, m.audibleId.orEmpty())
                put(MetadataFieldKey.DoubanRating, fmtDouble(m.doubanRating))
                put(MetadataFieldKey.DoubanReviewCount, fmtInt(m.doubanReviewCount))
                put(MetadataFieldKey.LubimyczytacRating, fmtDouble(m.lubimyczytacRating))
                put(MetadataFieldKey.RanobedbRating, fmtDouble(m.ranobedbRating))
                put(MetadataFieldKey.AudibleRating, fmtDouble(m.audibleRating))
                put(MetadataFieldKey.AudibleReviewCount, fmtInt(m.audibleReviewCount))
            },
        )
    }
}

data class SearchForm(
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val asin: String = "",
    val providers: Set<MetadataProvider> = searchableProviders.toSet(),
)

enum class SearchPhase { Idle, Running, Done, Error }

data class EditMetadataSuccess(
    val bookId: String,
    val server: Server? = null,
    val grimmoryBookId: Long? = null,
    val original: GrimmoryBookMetadata,
    val originalEditable: EditableMetadata,
    val edited: EditableMetadata,
    val clearFlags: Set<MetadataFieldKey> = emptySet(),
    val searchForm: SearchForm = SearchForm(),
    val searchPhase: SearchPhase = SearchPhase.Idle,
    val searchResults: List<GrimmoryBookMetadata> = emptyList(),
    val searchError: String? = null,
    val selectedCandidate: GrimmoryBookMetadata? = null,
    val saving: Boolean = false,
    val readOnly: Boolean = false,
) {
    val isLocal: Boolean get() = server == null
    val isDirty: Boolean get() = edited != originalEditable || clearFlags.isNotEmpty()
}

sealed interface EditMetadataUiState {
    data object Loading : EditMetadataUiState
    data class Error(val message: String) : EditMetadataUiState
    data class Success(val state: EditMetadataSuccess) : EditMetadataUiState
}

@HiltViewModel
class EditMetadataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val metadataClient: MetadataClient,
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _uiState = MutableStateFlow<EditMetadataUiState>(EditMetadataUiState.Loading)
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun dismissMessage() { _message.value = null }

    private fun load() {
        viewModelScope.launch {
            val book: Book = bookRepository.getById(bookId) ?: run {
                _uiState.value = EditMetadataUiState.Error("Book not found")
                return@launch
            }
            val serverId = book.serverId
            val grimmoryBookId = book.grimmoryBookId
            val server = serverId?.let { serverRepository.getById(it) }
            val isGrimmory = server?.isGrimmory == true && grimmoryBookId != null

            if (isGrimmory) {
                loadGrimmoryMetadata(book, server!!, grimmoryBookId!!)
            } else {
                loadLocalMetadata(book)
            }
        }
    }

    private suspend fun loadGrimmoryMetadata(book: Book, server: Server, grimmoryBookId: Long) {
        metadataClient.getBookMetadata(server.url, server.id, grimmoryBookId)
            .onSuccess { metadata ->
                _uiState.value = EditMetadataUiState.Success(
                    EditMetadataSuccess(
                        bookId = bookId,
                        server = server,
                        grimmoryBookId = grimmoryBookId,
                        original = metadata,
                        originalEditable = EditableMetadata.from(metadata),
                        edited = EditableMetadata.from(metadata),
                        searchForm = SearchForm(
                            title = metadata.title.orEmpty(),
                            author = metadata.authors?.firstOrNull().orEmpty(),
                            isbn = metadata.isbn13.orEmpty(),
                            asin = metadata.asin.orEmpty(),
                        ),
                        readOnly = metadata.allMetadataLocked == true,
                    ),
                )
            }
            .onFailure { e ->
                Timber.e(e, "Failed to load metadata")
                _uiState.value = EditMetadataUiState.Error(friendlyErrorMessage(e))
            }
    }

    private fun loadLocalMetadata(book: Book) {
        val metadata = GrimmoryBookMetadata(
            title = book.title,
            subtitle = null,
            authors = book.author?.let { listOf(it) },
            publisher = book.publisher,
            publishedDate = book.publishedDate,
            description = book.description,
            seriesName = book.series,
            seriesNumber = book.seriesIndex,
            language = book.language,
            pageCount = book.pageCount,
            categories = book.subjects?.parseCommaSeparated()?.toSet(),
        )
        val editable = EditableMetadata.from(metadata)
        _uiState.value = EditMetadataUiState.Success(
            EditMetadataSuccess(
                bookId = bookId,
                original = metadata,
                originalEditable = editable,
                edited = editable,
            ),
        )
    }

    fun editField(key: MetadataFieldKey, value: String) = updateSuccess { s ->
        if (s.readOnly || isLocked(s.original, key)) return@updateSuccess s
        val nextClear = s.clearFlags.toMutableSet().apply {
            if (value.isBlank() && originalHasValue(s.original, key)) add(key) else remove(key)
        }
        s.copy(edited = s.edited.set(key, value), clearFlags = nextClear)
    }

    fun updateSearchForm(transform: (SearchForm) -> SearchForm) = updateSuccess { s ->
        s.copy(searchForm = transform(s.searchForm))
    }

    fun toggleProvider(provider: MetadataProvider) = updateSuccess { s ->
        val providers = s.searchForm.providers.toMutableSet()
        if (!providers.remove(provider)) providers.add(provider)
        s.copy(searchForm = s.searchForm.copy(providers = providers))
    }

    fun startSearch() {
        val current = currentSuccess() ?: return
        if (current.isLocal || current.server == null || current.grimmoryBookId == null) return
        searchJob?.cancel()
        updateSuccess { it.copy(searchPhase = SearchPhase.Running, searchResults = emptyList(), searchError = null) }
        searchJob = viewModelScope.launch {
            try {
                val req = FetchMetadataRequest(
                    providers = current.searchForm.providers.toList().ifEmpty { searchableProviders },
                    title = current.searchForm.title.ifBlank { null },
                    author = current.searchForm.author.ifBlank { null },
                    isbn = current.searchForm.isbn.ifBlank { null },
                    asin = current.searchForm.asin.ifBlank { null },
                )
                metadataClient.searchProviders(
                    baseUrl = current.server!!.url,
                    serverId = current.server.id,
                    bookId = current.grimmoryBookId!!,
                    request = req,
                ).collect { event ->
                    when (event) {
                        is MetadataSearchEvent.Candidate -> updateSuccess {
                            it.copy(searchResults = it.searchResults + event.metadata)
                        }
                        is MetadataSearchEvent.Error -> updateSuccess {
                            it.copy(searchError = event.message)
                        }
                        MetadataSearchEvent.Done -> updateSuccess {
                            it.copy(searchPhase = SearchPhase.Done)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Metadata search failed")
                updateSuccess {
                    it.copy(searchPhase = SearchPhase.Error, searchError = friendlyErrorMessage(e))
                }
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        updateSuccess { it.copy(searchPhase = SearchPhase.Idle) }
    }

    fun selectCandidate(candidate: GrimmoryBookMetadata) = updateSuccess {
        it.copy(selectedCandidate = candidate)
    }

    fun closeCandidate() = updateSuccess { it.copy(selectedCandidate = null) }

    fun applyFetchedField(key: MetadataFieldKey) = updateSuccess { s ->
        val candidate = s.selectedCandidate ?: return@updateSuccess s
        if (s.readOnly || isLocked(s.original, key)) return@updateSuccess s
        val fetched = EditableMetadata.from(candidate).get(key)
        if (fetched.isBlank()) return@updateSuccess s
        s.copy(
            edited = s.edited.set(key, fetched),
            clearFlags = s.clearFlags - key,
        )
    }

    fun applyAllFetched() = updateSuccess { s ->
        val candidate = s.selectedCandidate ?: return@updateSuccess s
        if (s.readOnly) return@updateSuccess s
        val fetched = EditableMetadata.from(candidate)
        var next = s.edited
        val nextClear = s.clearFlags.toMutableSet()
        MetadataFieldKey.entries.forEach { key ->
            if (isLocked(s.original, key)) return@forEach
            val v = fetched.get(key)
            if (v.isNotBlank()) {
                next = next.set(key, v)
                nextClear.remove(key)
            }
        }
        s.copy(edited = next, clearFlags = nextClear)
    }

    fun save() {
        val s = currentSuccess() ?: return
        if (s.saving || s.readOnly || !s.isDirty) return
        updateSuccess { it.copy(saving = true) }
        viewModelScope.launch {
            if (s.isLocal) {
                saveLocal(s)
            } else {
                saveGrimmory(s)
            }
        }
    }

    private suspend fun saveGrimmory(s: EditMetadataSuccess) {
        val wrapper = buildWrapper(s)
        metadataClient.updateMetadata(
            baseUrl = s.server!!.url,
            serverId = s.server.id,
            bookId = s.grimmoryBookId!!,
            wrapper = wrapper,
            replaceMode = MetadataReplaceMode.REPLACE_WHEN_PROVIDED,
        ).onSuccess { updated ->
            _message.value = "Metadata saved"
            val editable = EditableMetadata.from(updated)
            updateSuccess {
                it.copy(
                    saving = false,
                    original = updated,
                    originalEditable = editable,
                    edited = editable,
                    clearFlags = emptySet(),
                    selectedCandidate = null,
                )
            }
            _saved.value = true
        }.onFailure { e ->
            Timber.e(e, "Save metadata failed")
            updateSuccess { it.copy(saving = false) }
            _message.value = friendlyErrorMessage(e)
        }
    }

    private suspend fun saveLocal(s: EditMetadataSuccess) {
        try {
            val e = s.edited
            bookRepository.updateLocalBookMetadata(
                bookId = s.bookId,
                title = e.get(MetadataFieldKey.Title).ifBlank { "Untitled" },
                author = e.get(MetadataFieldKey.Authors).ifBlank { null },
                description = e.get(MetadataFieldKey.Description).ifBlank { null },
                series = e.get(MetadataFieldKey.SeriesName).ifBlank { null },
                seriesIndex = e.get(MetadataFieldKey.SeriesNumber).toFloatOrNull(),
                publisher = e.get(MetadataFieldKey.Publisher).ifBlank { null },
                language = e.get(MetadataFieldKey.Language).ifBlank { null },
                subjects = e.get(MetadataFieldKey.Categories).ifBlank { null },
                pageCount = e.get(MetadataFieldKey.PageCount).toIntOrNull(),
                publishedDate = e.get(MetadataFieldKey.PublishedDate).ifBlank { null },
            )
            val updated = GrimmoryBookMetadata(
                title = e.get(MetadataFieldKey.Title).ifBlank { "Untitled" },
                authors = e.get(MetadataFieldKey.Authors).parseCommaSeparated()?.ifEmpty { null },
                description = e.get(MetadataFieldKey.Description).ifBlank { null },
                seriesName = e.get(MetadataFieldKey.SeriesName).ifBlank { null },
                seriesNumber = e.get(MetadataFieldKey.SeriesNumber).toFloatOrNull(),
                publisher = e.get(MetadataFieldKey.Publisher).ifBlank { null },
                language = e.get(MetadataFieldKey.Language).ifBlank { null },
                categories = e.get(MetadataFieldKey.Categories).parseCommaSeparated()?.toSet()?.ifEmpty { null },
                pageCount = e.get(MetadataFieldKey.PageCount).toIntOrNull(),
                publishedDate = e.get(MetadataFieldKey.PublishedDate).ifBlank { null },
            )
            val editable = EditableMetadata.from(updated)
            _message.value = "Metadata saved"
            updateSuccess {
                it.copy(
                    saving = false,
                    original = updated,
                    originalEditable = editable,
                    edited = editable,
                    clearFlags = emptySet(),
                )
            }
            _saved.value = true
        } catch (e: Exception) {
            Timber.e(e, "Save local metadata failed")
            updateSuccess { it.copy(saving = false) }
            _message.value = friendlyErrorMessage(e)
        }
    }

    private fun buildWrapper(s: EditMetadataSuccess): MetadataUpdateWrapper {
        val e = s.edited
        val cl = s.clearFlags
        fun txt(key: MetadataFieldKey): String? =
            if (key in cl) null else e.get(key).takeIf { it.isNotBlank() }
        fun csvList(key: MetadataFieldKey): List<String>? = txt(key)?.parseCommaSeparated()
        fun csvSet(key: MetadataFieldKey): Set<String>? = csvList(key)?.toSet()
        fun flt(key: MetadataFieldKey): Float? = txt(key)?.toFloatOrNull()
        fun dbl(key: MetadataFieldKey): Double? = txt(key)?.toDoubleOrNull()
        fun int(key: MetadataFieldKey): Int? = txt(key)?.toIntOrNull()
        fun bool(key: MetadataFieldKey): Boolean? = txt(key)?.toBooleanStrictOrNull()

        val metadata = GrimmoryBookMetadata(
            title = txt(MetadataFieldKey.Title),
            subtitle = txt(MetadataFieldKey.Subtitle),
            authors = csvList(MetadataFieldKey.Authors),
            publisher = txt(MetadataFieldKey.Publisher),
            publishedDate = txt(MetadataFieldKey.PublishedDate),
            description = txt(MetadataFieldKey.Description),
            seriesName = txt(MetadataFieldKey.SeriesName),
            seriesNumber = flt(MetadataFieldKey.SeriesNumber),
            seriesTotal = int(MetadataFieldKey.SeriesTotal),
            language = txt(MetadataFieldKey.Language),
            isbn13 = txt(MetadataFieldKey.Isbn13),
            isbn10 = txt(MetadataFieldKey.Isbn10),
            pageCount = int(MetadataFieldKey.PageCount),
            categories = csvSet(MetadataFieldKey.Categories),
            moods = csvSet(MetadataFieldKey.Moods),
            tags = csvSet(MetadataFieldKey.Tags),
            narrator = txt(MetadataFieldKey.Narrator),
            abridged = bool(MetadataFieldKey.Abridged),
            ageRating = int(MetadataFieldKey.AgeRating),
            contentRating = txt(MetadataFieldKey.ContentRating),
            asin = txt(MetadataFieldKey.Asin),
            goodreadsId = txt(MetadataFieldKey.GoodreadsId),
            googleId = txt(MetadataFieldKey.GoogleId),
            hardcoverId = txt(MetadataFieldKey.HardcoverId),
            hardcoverBookId = txt(MetadataFieldKey.HardcoverBookId),
            externalUrl = txt(MetadataFieldKey.ExternalUrl),
            amazonRating = dbl(MetadataFieldKey.AmazonRating),
            amazonReviewCount = int(MetadataFieldKey.AmazonReviewCount),
            goodreadsRating = dbl(MetadataFieldKey.GoodreadsRating),
            goodreadsReviewCount = int(MetadataFieldKey.GoodreadsReviewCount),
            hardcoverRating = dbl(MetadataFieldKey.HardcoverRating),
            hardcoverReviewCount = int(MetadataFieldKey.HardcoverReviewCount),
            comicvineId = txt(MetadataFieldKey.ComicvineId),
            doubanId = txt(MetadataFieldKey.DoubanId),
            lubimyczytacId = txt(MetadataFieldKey.LubimyczytacId),
            ranobedbId = txt(MetadataFieldKey.RanobedbId),
            audibleId = txt(MetadataFieldKey.AudibleId),
            doubanRating = dbl(MetadataFieldKey.DoubanRating),
            doubanReviewCount = int(MetadataFieldKey.DoubanReviewCount),
            lubimyczytacRating = dbl(MetadataFieldKey.LubimyczytacRating),
            ranobedbRating = dbl(MetadataFieldKey.RanobedbRating),
            audibleRating = dbl(MetadataFieldKey.AudibleRating),
            audibleReviewCount = int(MetadataFieldKey.AudibleReviewCount),
        )
        fun c(key: MetadataFieldKey) = key in cl
        val flags = MetadataClearFlags(
            title = c(MetadataFieldKey.Title),
            subtitle = c(MetadataFieldKey.Subtitle),
            publisher = c(MetadataFieldKey.Publisher),
            publishedDate = c(MetadataFieldKey.PublishedDate),
            description = c(MetadataFieldKey.Description),
            seriesName = c(MetadataFieldKey.SeriesName),
            seriesNumber = c(MetadataFieldKey.SeriesNumber),
            seriesTotal = c(MetadataFieldKey.SeriesTotal),
            isbn13 = c(MetadataFieldKey.Isbn13),
            isbn10 = c(MetadataFieldKey.Isbn10),
            pageCount = c(MetadataFieldKey.PageCount),
            language = c(MetadataFieldKey.Language),
            authors = c(MetadataFieldKey.Authors),
            categories = c(MetadataFieldKey.Categories),
            moods = c(MetadataFieldKey.Moods),
            tags = c(MetadataFieldKey.Tags),
            narrator = c(MetadataFieldKey.Narrator),
            abridged = c(MetadataFieldKey.Abridged),
            ageRating = c(MetadataFieldKey.AgeRating),
            contentRating = c(MetadataFieldKey.ContentRating),
            asin = c(MetadataFieldKey.Asin),
            goodreadsId = c(MetadataFieldKey.GoodreadsId),
            googleId = c(MetadataFieldKey.GoogleId),
            hardcoverId = c(MetadataFieldKey.HardcoverId),
            hardcoverBookId = c(MetadataFieldKey.HardcoverBookId),
            externalUrl = c(MetadataFieldKey.ExternalUrl),
            amazonRating = c(MetadataFieldKey.AmazonRating),
            amazonReviewCount = c(MetadataFieldKey.AmazonReviewCount),
            goodreadsRating = c(MetadataFieldKey.GoodreadsRating),
            goodreadsReviewCount = c(MetadataFieldKey.GoodreadsReviewCount),
            hardcoverRating = c(MetadataFieldKey.HardcoverRating),
            hardcoverReviewCount = c(MetadataFieldKey.HardcoverReviewCount),
            comicvineId = c(MetadataFieldKey.ComicvineId),
            doubanId = c(MetadataFieldKey.DoubanId),
            lubimyczytacId = c(MetadataFieldKey.LubimyczytacId),
            ranobedbId = c(MetadataFieldKey.RanobedbId),
            audibleId = c(MetadataFieldKey.AudibleId),
            doubanRating = c(MetadataFieldKey.DoubanRating),
            doubanReviewCount = c(MetadataFieldKey.DoubanReviewCount),
            lubimyczytacRating = c(MetadataFieldKey.LubimyczytacRating),
            ranobedbRating = c(MetadataFieldKey.RanobedbRating),
            audibleRating = c(MetadataFieldKey.AudibleRating),
            audibleReviewCount = c(MetadataFieldKey.AudibleReviewCount),
        )
        return MetadataUpdateWrapper(metadata = metadata, clearFlags = flags)
    }

    private fun currentSuccess(): EditMetadataSuccess? =
        (_uiState.value as? EditMetadataUiState.Success)?.state

    private fun updateSuccess(transform: (EditMetadataSuccess) -> EditMetadataSuccess) {
        _uiState.update { current ->
            if (current is EditMetadataUiState.Success) {
                EditMetadataUiState.Success(transform(current.state))
            } else current
        }
    }

    private fun originalHasValue(m: GrimmoryBookMetadata, key: MetadataFieldKey): Boolean =
        EditableMetadata.from(m).get(key).isNotBlank()
}

fun isLocked(m: GrimmoryBookMetadata, key: MetadataFieldKey): Boolean {
    if (m.allMetadataLocked == true) return true
    return when (key) {
        MetadataFieldKey.Title -> m.titleLocked
        MetadataFieldKey.Subtitle -> m.subtitleLocked
        MetadataFieldKey.Authors -> m.authorsLocked
        MetadataFieldKey.SeriesName -> m.seriesNameLocked
        MetadataFieldKey.SeriesNumber -> m.seriesNumberLocked
        MetadataFieldKey.SeriesTotal -> m.seriesTotalLocked
        MetadataFieldKey.Publisher -> m.publisherLocked
        MetadataFieldKey.PublishedDate -> m.publishedDateLocked
        MetadataFieldKey.Description -> m.descriptionLocked
        MetadataFieldKey.Language -> m.languageLocked
        MetadataFieldKey.Isbn13 -> m.isbn13Locked
        MetadataFieldKey.Isbn10 -> m.isbn10Locked
        MetadataFieldKey.PageCount -> m.pageCountLocked
        MetadataFieldKey.Categories -> m.categoriesLocked
        MetadataFieldKey.Moods -> m.moodsLocked
        MetadataFieldKey.Tags -> m.tagsLocked
        MetadataFieldKey.Narrator -> m.narratorLocked
        MetadataFieldKey.Abridged -> m.abridgedLocked
        MetadataFieldKey.AgeRating -> m.ageRatingLocked
        MetadataFieldKey.ContentRating -> m.contentRatingLocked
        MetadataFieldKey.Asin -> m.asinLocked
        MetadataFieldKey.GoodreadsId -> m.goodreadsIdLocked
        MetadataFieldKey.GoogleId -> m.googleIdLocked
        MetadataFieldKey.HardcoverId -> m.hardcoverIdLocked
        MetadataFieldKey.HardcoverBookId -> m.hardcoverBookIdLocked
        MetadataFieldKey.ExternalUrl -> m.externalUrlLocked
        MetadataFieldKey.AmazonRating -> m.amazonRatingLocked
        MetadataFieldKey.AmazonReviewCount -> m.amazonReviewCountLocked
        MetadataFieldKey.GoodreadsRating -> m.goodreadsRatingLocked
        MetadataFieldKey.GoodreadsReviewCount -> m.goodreadsReviewCountLocked
        MetadataFieldKey.HardcoverRating -> m.hardcoverRatingLocked
        MetadataFieldKey.HardcoverReviewCount -> m.hardcoverReviewCountLocked
        MetadataFieldKey.ComicvineId -> m.comicvineIdLocked
        MetadataFieldKey.DoubanId -> m.doubanIdLocked
        MetadataFieldKey.LubimyczytacId -> m.lubimyczytacIdLocked
        MetadataFieldKey.RanobedbId -> m.ranobedbIdLocked
        MetadataFieldKey.AudibleId -> m.audibleIdLocked
        MetadataFieldKey.DoubanRating -> m.doubanRatingLocked
        MetadataFieldKey.DoubanReviewCount -> m.doubanReviewCountLocked
        MetadataFieldKey.LubimyczytacRating -> m.lubimyczytacRatingLocked
        MetadataFieldKey.RanobedbRating -> m.ranobedbRatingLocked
        MetadataFieldKey.AudibleRating -> m.audibleRatingLocked
        MetadataFieldKey.AudibleReviewCount -> m.audibleReviewCountLocked
    } == true
}
