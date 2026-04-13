package com.ember.reader.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps PrimeNG icon class names (as stored by Grimmory) to Material Design icons.
 * Grimmory stores icons as "pi pi-<name>" strings.
 */
object GrimmoryIconMapper {

    fun resolve(primeIcon: String?): ImageVector? {
        if (primeIcon.isNullOrBlank()) return null
        val name = primeIcon.removePrefix("pi pi-").removePrefix("pi-")
        return iconMap[name]
    }

    private val iconMap: Map<String, ImageVector> = mapOf(
        // Books & reading
        "book" to Icons.AutoMirrored.Filled.MenuBook,
        "bookmark" to Icons.Default.Bookmark,
        "bookmark-fill" to Icons.Default.Bookmark,
        "bookmarks" to Icons.Default.Bookmarks,

        // People
        "user" to Icons.Default.Person,
        "users" to Icons.Default.People,
        "user-edit" to Icons.Default.Person,
        "user-plus" to Icons.Default.Person,

        // Files & folders
        "folder" to Icons.Default.Folder,
        "folder-open" to Icons.Default.FolderOpen,
        "file" to Icons.AutoMirrored.Filled.LibraryBooks,
        "file-pdf" to Icons.AutoMirrored.Filled.LibraryBooks,
        "image" to Icons.Default.Image,
        "images" to Icons.Default.Image,

        // Favorites & ratings
        "heart" to Icons.Default.Favorite,
        "heart-fill" to Icons.Default.Favorite,
        "star" to Icons.Default.Star,
        "star-fill" to Icons.Default.Star,
        "star-half" to Icons.Default.StarBorder,
        "star-half-fill" to Icons.Default.StarBorder,
        "thumbs-up" to Icons.Default.Favorite,
        "thumbs-up-fill" to Icons.Default.Favorite,
        "trophy" to Icons.Default.Star,
        "crown" to Icons.Default.Star,

        // Navigation & UI
        "home" to Icons.Default.Home,
        "search" to Icons.Default.Search,
        "cog" to Icons.Default.Settings,
        "list" to Icons.AutoMirrored.Filled.List,
        "list-check" to Icons.AutoMirrored.Filled.List,
        "th-large" to Icons.Default.ViewModule,
        "eye" to Icons.Default.Visibility,
        "eye-slash" to Icons.Default.VisibilityOff,
        "globe" to Icons.Default.Language,
        "language" to Icons.Default.Language,
        "map" to Icons.Default.Map,
        "map-marker" to Icons.Default.Place,
        "compass" to Icons.Default.Explore,
        "link" to Icons.Default.Link,
        "external-link" to Icons.Default.Link,
        "info" to Icons.Default.Info,
        "info-circle" to Icons.Default.Info,
        "tag" to Icons.Default.Tag,
        "tags" to Icons.Default.Tag,
        "filter" to Icons.AutoMirrored.Filled.Label,
        "palette" to Icons.Default.Palette,

        // Actions
        "pencil" to Icons.Default.Edit,
        "pen-to-square" to Icons.Default.Edit,
        "download" to Icons.Default.Download,
        "share-alt" to Icons.Default.Share,
        "lock" to Icons.Default.Lock,
        "lock-open" to Icons.Default.LockOpen,
        "unlock" to Icons.Default.LockOpen,
        "save" to Icons.Default.Bookmark,
        "shopping-cart" to Icons.Default.ShoppingCart,
        "shop" to Icons.Default.Storefront,
        "shopping-bag" to Icons.Default.ShoppingCart,
        "credit-card" to Icons.Default.CreditCard,

        // Time
        "clock" to Icons.Default.History,
        "history" to Icons.Default.History,
        "calendar" to Icons.Default.CalendarMonth,
        "calendar-clock" to Icons.Default.CalendarMonth,
        "calendar-plus" to Icons.Default.CalendarMonth,
        "calendar-minus" to Icons.Default.CalendarMonth,
        "calendar-times" to Icons.Default.CalendarMonth,
        "hourglass" to Icons.Default.History,
        "stopwatch" to Icons.Default.History,

        // Status
        "check" to Icons.Default.CheckCircle,
        "check-circle" to Icons.Default.CheckCircle,
        "check-square" to Icons.Default.CheckCircle,
        "shield" to Icons.Default.Shield,
        "verified" to Icons.Default.CheckCircle,

        // Communication
        "envelope" to Icons.Default.Email,
        "inbox" to Icons.Default.Inbox,
        "send" to Icons.Default.Email,
        "comment" to Icons.Default.Email,
        "comments" to Icons.Default.Email,

        // Misc
        "sun" to Icons.Default.LightMode,
        "moon" to Icons.Default.DarkMode,
        "cloud" to Icons.Default.Cloud,
        "database" to Icons.Default.Storage,
        "server" to Icons.Default.Storage,
        "warehouse" to Icons.Default.Warehouse,
        "graduation-cap" to Icons.Default.School,
        "play-circle" to Icons.Default.PlayCircle,
        "lightbulb" to Icons.Default.AutoAwesome,
        "sparkles" to Icons.Default.AutoAwesome,
        "gift" to Icons.Default.AutoStories,
        "building" to Icons.Default.Storefront,
        "building-columns" to Icons.Default.Storefront
    )
}
