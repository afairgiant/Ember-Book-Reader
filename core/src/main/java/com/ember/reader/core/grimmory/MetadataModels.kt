package com.ember.reader.core.grimmory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Grimmory book metadata DTO. Subset of the backend `BookMetadata` — we only serialize
 * the fields Ember's editor cares about. Ktor's global JSON config has `ignoreUnknownKeys = true`,
 * so extra fields from the server are silently dropped on decode.
 *
 * On encode (PUT /metadata) the backend tolerates missing fields; only fields that are
 * non-null are applied per `replaceMode`.
 */
@Serializable
data class GrimmoryBookMetadata(
    val bookId: Long? = null,

    // Basic / top fields
    val title: String? = null,
    val subtitle: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,

    // Series
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    val seriesTotal: Int? = null,

    // Book details
    val isbn13: String? = null,
    val isbn10: String? = null,
    val pageCount: Int? = null,
    val language: String? = null,

    // Audiobook
    val narrator: String? = null,
    val abridged: Boolean? = null,

    // Content rating
    val ageRating: Int? = null,
    val contentRating: String? = null,

    // Array fields
    val authors: List<String>? = null,
    val categories: Set<String>? = null,
    val moods: Set<String>? = null,
    val tags: Set<String>? = null,

    // Provider IDs
    val asin: String? = null,
    val goodreadsId: String? = null,
    val comicvineId: String? = null,
    val hardcoverId: String? = null,
    val hardcoverBookId: String? = null,
    val googleId: String? = null,
    val doubanId: String? = null,
    val lubimyczytacId: String? = null,
    val ranobedbId: String? = null,
    val audibleId: String? = null,
    val externalUrl: String? = null,

    // Provider ratings
    val amazonRating: Double? = null,
    val amazonReviewCount: Int? = null,
    val goodreadsRating: Double? = null,
    val goodreadsReviewCount: Int? = null,
    val hardcoverRating: Double? = null,
    val hardcoverReviewCount: Int? = null,
    val doubanRating: Double? = null,
    val doubanReviewCount: Int? = null,
    val lubimyczytacRating: Double? = null,
    val ranobedbRating: Double? = null,
    val audibleRating: Double? = null,
    val audibleReviewCount: Int? = null,

    // Misc
    val thumbnailUrl: String? = null,
    val provider: MetadataProvider? = null,

    // Locks
    val allMetadataLocked: Boolean? = null,
    val titleLocked: Boolean? = null,
    val subtitleLocked: Boolean? = null,
    val publisherLocked: Boolean? = null,
    val publishedDateLocked: Boolean? = null,
    val descriptionLocked: Boolean? = null,
    val seriesNameLocked: Boolean? = null,
    val seriesNumberLocked: Boolean? = null,
    val seriesTotalLocked: Boolean? = null,
    val isbn13Locked: Boolean? = null,
    val isbn10Locked: Boolean? = null,
    val asinLocked: Boolean? = null,
    val pageCountLocked: Boolean? = null,
    val languageLocked: Boolean? = null,
    val authorsLocked: Boolean? = null,
    val categoriesLocked: Boolean? = null,
    val moodsLocked: Boolean? = null,
    val tagsLocked: Boolean? = null,
    val coverLocked: Boolean? = null,
    val goodreadsIdLocked: Boolean? = null,
    val comicvineIdLocked: Boolean? = null,
    val hardcoverIdLocked: Boolean? = null,
    val hardcoverBookIdLocked: Boolean? = null,
    val googleIdLocked: Boolean? = null,
    val doubanIdLocked: Boolean? = null,
    val lubimyczytacIdLocked: Boolean? = null,
    val ranobedbIdLocked: Boolean? = null,
    val audibleIdLocked: Boolean? = null,
    val externalUrlLocked: Boolean? = null,
    val amazonRatingLocked: Boolean? = null,
    val amazonReviewCountLocked: Boolean? = null,
    val goodreadsRatingLocked: Boolean? = null,
    val goodreadsReviewCountLocked: Boolean? = null,
    val hardcoverRatingLocked: Boolean? = null,
    val hardcoverReviewCountLocked: Boolean? = null,
    val doubanRatingLocked: Boolean? = null,
    val doubanReviewCountLocked: Boolean? = null,
    val lubimyczytacRatingLocked: Boolean? = null,
    val ranobedbRatingLocked: Boolean? = null,
    val audibleRatingLocked: Boolean? = null,
    val audibleReviewCountLocked: Boolean? = null,
    val narratorLocked: Boolean? = null,
    val abridgedLocked: Boolean? = null,
    val ageRatingLocked: Boolean? = null,
    val contentRatingLocked: Boolean? = null
)

/** Matches backend `MetadataProvider` enum. Only the providers Ember surfaces in search. */
@Serializable
enum class MetadataProvider {
    @SerialName("Amazon") Amazon,

    @SerialName("GoodReads") GoodReads,

    @SerialName("Google") Google,

    @SerialName("Hardcover") Hardcover,

    // Catch-all for providers that come back in results but aren't offered in the search UI.
    @SerialName("Comicvine") Comicvine,

    @SerialName("Douban") Douban,

    @SerialName("Lubimyczytac") Lubimyczytac,

    @SerialName("Ranobedb") Ranobedb,

    @SerialName("Audible") Audible
}

/** Providers Ember exposes in the search form. Order matches UI. */
val searchableProviders: List<MetadataProvider> = listOf(
    MetadataProvider.Amazon,
    MetadataProvider.GoodReads,
    MetadataProvider.Google,
    MetadataProvider.Hardcover
)

@Serializable
data class FetchMetadataRequest(
    val providers: List<MetadataProvider>,
    val title: String? = null,
    val author: String? = null,
    val isbn: String? = null,
    val asin: String? = null
)

@Serializable
enum class MetadataReplaceMode {
    @SerialName("REPLACE_ALL") REPLACE_ALL,

    @SerialName("REPLACE_WHEN_PROVIDED") REPLACE_WHEN_PROVIDED
}

@Serializable
data class MetadataUpdateWrapper(
    val metadata: GrimmoryBookMetadata,
    val clearFlags: MetadataClearFlags = MetadataClearFlags()
)

/** Set flags to true to null-out the corresponding field on save. */
@Serializable
data class MetadataClearFlags(
    val title: Boolean = false,
    val subtitle: Boolean = false,
    val publisher: Boolean = false,
    val publishedDate: Boolean = false,
    val description: Boolean = false,
    val seriesName: Boolean = false,
    val seriesNumber: Boolean = false,
    val seriesTotal: Boolean = false,
    val isbn13: Boolean = false,
    val isbn10: Boolean = false,
    val asin: Boolean = false,
    val pageCount: Boolean = false,
    val language: Boolean = false,
    val narrator: Boolean = false,
    val abridged: Boolean = false,
    val ageRating: Boolean = false,
    val contentRating: Boolean = false,
    val authors: Boolean = false,
    val categories: Boolean = false,
    val moods: Boolean = false,
    val tags: Boolean = false,
    val cover: Boolean = false,
    val goodreadsId: Boolean = false,
    val comicvineId: Boolean = false,
    val hardcoverId: Boolean = false,
    val hardcoverBookId: Boolean = false,
    val googleId: Boolean = false,
    val doubanId: Boolean = false,
    val lubimyczytacId: Boolean = false,
    val ranobedbId: Boolean = false,
    val audibleId: Boolean = false,
    val externalUrl: Boolean = false,
    val amazonRating: Boolean = false,
    val amazonReviewCount: Boolean = false,
    val goodreadsRating: Boolean = false,
    val goodreadsReviewCount: Boolean = false,
    val hardcoverRating: Boolean = false,
    val hardcoverReviewCount: Boolean = false,
    val doubanRating: Boolean = false,
    val doubanReviewCount: Boolean = false,
    val lubimyczytacRating: Boolean = false,
    val ranobedbRating: Boolean = false,
    val audibleRating: Boolean = false,
    val audibleReviewCount: Boolean = false,
    val reviews: Boolean = false,
    val audiobookCover: Boolean = false
)

/** Streaming search event emitted by [MetadataClient.searchProviders]. */
sealed interface MetadataSearchEvent {
    data class Candidate(val metadata: GrimmoryBookMetadata) : MetadataSearchEvent
    data class Error(val message: String) : MetadataSearchEvent
    data object Done : MetadataSearchEvent
}
