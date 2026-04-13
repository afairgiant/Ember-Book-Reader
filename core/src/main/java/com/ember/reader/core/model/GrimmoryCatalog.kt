package com.ember.reader.core.model

enum class CatalogEntryType {
    CONTINUE_READING,
    RECENTLY_ADDED,
    LIBRARY,
    SHELF,
    MAGIC_SHELF,
    SERIES,
    AUTHORS,
    ALL_BOOKS
}

sealed interface GrimmoryCatalogEntry {
    val id: String
    val title: String
    val subtitle: String?
    val href: String
    val type: CatalogEntryType
    val serverIcon: String?
}

data class GrimmoryCatalogLibrary(
    val libraryId: Long,
    override val title: String,
    val bookCount: Int,
    override val serverIcon: String?
) : GrimmoryCatalogEntry {
    override val id: String = "grimmory:library:$libraryId"
    override val subtitle: String = "$bookCount books"
    override val href: String = "grimmory:libraryId=$libraryId"
    override val type: CatalogEntryType = CatalogEntryType.LIBRARY
}

data class GrimmoryCatalogShelf(
    val shelfId: Long,
    override val title: String,
    val bookCount: Int,
    val publicShelf: Boolean,
    override val serverIcon: String?
) : GrimmoryCatalogEntry {
    override val id: String = "grimmory:shelf:$shelfId"
    override val subtitle: String = "$bookCount books"
    override val href: String = "grimmory:shelfId=$shelfId"
    override val type: CatalogEntryType = CatalogEntryType.SHELF
}

data class GrimmoryCatalogMagicShelf(
    val magicShelfId: Long,
    override val title: String,
    val publicShelf: Boolean,
    override val serverIcon: String?,
    val iconType: String?
) : GrimmoryCatalogEntry {
    override val id: String = "grimmory:magicShelf:$magicShelfId"
    override val subtitle: String? = null
    override val href: String = "grimmory:magicShelfId=$magicShelfId"
    override val type: CatalogEntryType = CatalogEntryType.MAGIC_SHELF
}

data class GrimmoryCatalogMeta(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val href: String,
    override val type: CatalogEntryType
) : GrimmoryCatalogEntry {
    override val serverIcon: String? = null
}

data class GrimmoryCatalog(
    val serverName: String,
    val entries: List<GrimmoryCatalogEntry>
)
