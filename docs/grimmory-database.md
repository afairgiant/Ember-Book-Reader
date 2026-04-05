# Grimmory Database Schema

Reverse-engineered from JPA entities and Flyway migrations.
Database: MariaDB. ORM: Spring Data JPA / Hibernate.

---

## Entity Relationship Diagram

```mermaid
erDiagram
    %% ═══════════════════════════════════
    %% USERS & AUTH
    %% ═══════════════════════════════════

    users {
        bigint id PK
        varchar username UK "not null"
        varchar password_hash "not null"
        boolean is_default_password "not null"
        varchar name "not null"
        varchar email UK
        enum provisioning_method
        varchar oidc_subject
        varchar oidc_issuer
        varchar avatar_url
        datetime created_at "not null"
    }

    user_permissions {
        bigint id PK
        bigint user_id FK,UK "not null → users"
        boolean permission_admin "not null"
        boolean permission_upload
        boolean permission_download
        boolean permission_edit_metadata
        boolean permission_delete_book
        boolean permission_manipulate_library
        boolean permission_email_book
        boolean permission_sync_koreader
        boolean permission_access_opds
        boolean permission_sync_kobo
        boolean permission_manage_metadata_config
        boolean permission_access_bookdrop
        boolean permission_access_library_stats
        boolean permission_access_user_stats
        boolean permission_access_task_manager
        boolean permission_manage_global_preferences
        boolean permission_manage_icons
        boolean permission_manage_fonts
        boolean permission_demo_user
        boolean permission_bulk_auto_fetch_metadata
        boolean permission_bulk_custom_fetch_metadata
        boolean permission_bulk_edit_metadata
        boolean permission_bulk_regenerate_cover
        boolean permission_move_organize_files
        boolean permission_bulk_lock_unlock_metadata
        boolean permission_bulk_reset_booklore_read_progress
        boolean permission_bulk_reset_koreader_read_progress
        boolean permission_bulk_reset_book_read_status
    }

    refresh_token {
        bigint id PK
        varchar token UK "512 chars, not null"
        bigint user_id FK "→ users"
        timestamp expiry_date "not null"
        boolean revoked "default false"
        timestamp revocation_date
    }

    jwt_secret {
        bigint id PK
        varchar secret "not null"
        datetime created_at
    }

    oidc_session {
        bigint id PK
        bigint user_id FK "not null → users"
        varchar oidc_subject "not null"
        varchar oidc_issuer "not null"
        varchar oidc_session_id
        text id_token_hint
        timestamp created_at
        timestamp last_refreshed_at
        boolean revoked "default false"
    }

    oidc_group_mapping {
        bigint id PK
        varchar oidc_group_claim UK "not null"
        boolean is_admin "not null"
        text permissions
        text library_ids
        varchar description "500 chars"
        datetime created_at
        datetime updated_at
    }

    user_settings {
        bigint id PK
        bigint user_id FK "not null → users"
        varchar setting_key "100 chars, not null"
        varchar setting_value "not null"
        datetime created_at
        datetime updated_at
    }

    user_content_restriction {
        bigint id PK
        bigint user_id FK "not null → users"
        enum restriction_type "not null"
        enum mode "not null"
        varchar value "not null"
        datetime created_at
    }

    users ||--|| user_permissions : "has"
    users ||--o{ refresh_token : "has"
    users ||--o{ oidc_session : "has"
    users ||--o{ user_settings : "has"
    users ||--o{ user_content_restriction : "has"

    %% ═══════════════════════════════════
    %% LIBRARIES
    %% ═══════════════════════════════════

    library {
        bigint id PK
        varchar name "not null"
        varchar sort
        boolean watch
        varchar icon
        enum icon_type
        varchar file_naming_pattern
        varchar format_priority
        varchar allowed_formats
        enum organization_mode "default AUTO_DETECT"
        enum metadata_source "default EMBEDDED"
    }

    library_path {
        bigint id PK
        bigint library_id FK "not null → library"
        varchar path "not null"
    }

    user_library_mapping {
        bigint user_id FK "→ users"
        bigint library_id FK "→ library"
    }

    library ||--o{ library_path : "has paths"
    library ||--o{ user_library_mapping : "accessible by"
    users ||--o{ user_library_mapping : "can access"

    %% ═══════════════════════════════════
    %% BOOKS & FILES
    %% ═══════════════════════════════════

    book {
        bigint id PK
        bigint library_id FK "not null → library"
        bigint library_path_id FK "→ library_path"
        boolean is_physical "default false"
        float metadata_match_score
        timestamp metadata_updated_at
        timestamp added_on
        timestamp scanned_on
        varchar book_cover_hash "20 chars"
        varchar audiobook_cover_hash "20 chars"
        boolean deleted "default false"
        timestamp deleted_at
        text similar_books_json
    }

    book_file {
        bigint id PK
        bigint book_id FK "not null → book"
        varchar file_name "1000 chars, not null"
        varchar file_sub_path "512 chars, not null"
        boolean is_book "not null"
        boolean is_folder_based "default false"
        enum book_type "not null"
        boolean is_fixed_layout "default false"
        enum archive_type
        bigint file_size_kb
        varchar initial_hash "128 chars"
        varchar current_hash "128 chars"
        varchar alt_format_current_hash "128 chars"
        text description
        timestamp added_on
        bigint duration_seconds "audiobooks"
        int bitrate "audiobooks"
        int sample_rate "audiobooks"
        int channels "audiobooks"
        varchar codec "50 chars"
        int chapter_count "audiobooks"
        text chapters_json "audiobooks"
    }

    library ||--o{ book : "contains"
    book ||--o{ book_file : "has formats"

    %% ═══════════════════════════════════
    %% METADATA & AUTHORS
    %% ═══════════════════════════════════

    book_metadata {
        bigint book_id PK,FK "→ book"
        varchar title "not null"
        varchar subtitle
        varchar publisher
        date published_date
        text description
        varchar series_name
        float series_number
        int series_total
        int page_count
        varchar language "10 chars"
        varchar narrator "500 chars"
        boolean abridged
        int age_rating
        varchar content_rating "20 chars"
        varchar isbn_13 "13 chars"
        varchar isbn_10 "10 chars"
        varchar asin "10 chars"
        varchar goodreads_id "100 chars"
        varchar hardcover_id "100 chars"
        varchar google_id "100 chars"
        varchar comicvine_id "100 chars"
        varchar audible_id "100 chars"
        double rating
        int review_count
        double goodreads_rating
        int goodreads_review_count
        double amazon_rating
        int amazon_review_count
        double hardcover_rating
        int hardcover_review_count
        double audible_rating
        int audible_review_count
        timestamp cover_updated_on
        timestamp audiobook_cover_updated_on
        text embedding_vector
        text search_text
    }

    author {
        bigint id PK
        varchar name
        text description
        varchar asin "20 chars"
        boolean name_locked
        boolean description_locked
        boolean asin_locked
        boolean photo_locked
    }

    category {
        bigint id PK
        varchar name UK "not null"
    }

    mood {
        bigint id PK
        varchar name UK "not null"
    }

    tag {
        bigint id PK
        varchar name UK "not null"
    }

    public_book_review {
        bigint id PK
        bigint book_id FK "not null → book_metadata"
        enum metadata_provider "not null"
        varchar reviewer_name "512 chars"
        varchar title "512 chars"
        float rating
        timestamp date
        text body
        varchar country "512 chars"
        boolean spoiler
        int followers_count
        int text_reviews_count
    }

    book_metadata_author_mapping {
        bigint book_id FK "→ book_metadata"
        bigint author_id FK "→ author"
        int sort_order
    }

    book_metadata_category_mapping {
        bigint book_id FK "→ book_metadata"
        bigint category_id FK "→ category"
    }

    book_metadata_mood_mapping {
        bigint book_id FK "→ book_metadata"
        bigint mood_id FK "→ mood"
    }

    book_metadata_tag_mapping {
        bigint book_id FK "→ book_metadata"
        bigint tag_id FK "→ tag"
    }

    book ||--|| book_metadata : "has"
    book_metadata ||--o{ public_book_review : "has"
    book_metadata ||--o{ book_metadata_author_mapping : "written by"
    author ||--o{ book_metadata_author_mapping : "wrote"
    book_metadata ||--o{ book_metadata_category_mapping : "in"
    category ||--o{ book_metadata_category_mapping : "contains"
    book_metadata ||--o{ book_metadata_mood_mapping : "has"
    mood ||--o{ book_metadata_mood_mapping : "applies to"
    book_metadata ||--o{ book_metadata_tag_mapping : "tagged"
    tag ||--o{ book_metadata_tag_mapping : "applied to"

    %% ═══════════════════════════════════
    %% COMICS METADATA
    %% ═══════════════════════════════════

    comic_metadata {
        bigint book_id PK,FK "→ book_metadata"
        varchar issue_number
        varchar volume_name
        int volume_number
        varchar story_arc
        int story_arc_number
        varchar alternate_series
        varchar alternate_issue
        varchar imprint
        varchar format "50 chars"
        boolean black_and_white "default false"
        boolean manga "default false"
        varchar reading_direction "default ltr"
        varchar web_link
        text notes
    }

    comic_character {
        bigint id PK
        varchar name UK "not null"
    }

    comic_team {
        bigint id PK
        varchar name UK "not null"
    }

    comic_location {
        bigint id PK
        varchar name UK "not null"
    }

    comic_creator {
        bigint id PK
        varchar name UK "not null"
    }

    comic_metadata_creator_mapping {
        bigint id PK
        bigint book_id FK "→ comic_metadata"
        bigint creator_id FK "→ comic_creator"
        enum role "not null"
    }

    book_metadata ||--o| comic_metadata : "has (if comic)"
    comic_metadata }o--o{ comic_character : "features"
    comic_metadata }o--o{ comic_team : "features"
    comic_metadata }o--o{ comic_location : "set in"
    comic_metadata ||--o{ comic_metadata_creator_mapping : "created by"
    comic_creator ||--o{ comic_metadata_creator_mapping : "created"

    %% ═══════════════════════════════════
    %% SHELVES & COLLECTIONS
    %% ═══════════════════════════════════

    shelf {
        bigint id PK
        bigint user_id FK "not null → users"
        varchar name "not null"
        varchar sort
        varchar icon
        enum icon_type
        boolean is_public "default false"
    }

    magic_shelf {
        bigint id PK
        bigint user_id FK "not null → users"
        varchar name "not null"
        varchar icon
        enum icon_type
        json filter_json "not null"
        boolean is_public "default false"
        datetime created_at
        datetime updated_at
    }

    book_shelf_mapping {
        bigint book_id FK "→ book"
        bigint shelf_id FK "→ shelf"
    }

    users ||--o{ shelf : "owns"
    users ||--o{ magic_shelf : "owns"
    shelf ||--o{ book_shelf_mapping : "contains"
    book ||--o{ book_shelf_mapping : "on"

    %% ═══════════════════════════════════
    %% READING PROGRESS
    %% ═══════════════════════════════════

    user_book_progress {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        timestamp last_read_time
        enum read_status "UNREAD/READING/READ/DNF"
        timestamp date_finished
        int personal_rating "1-5"
        int pdf_progress "DEPRECATED"
        float pdf_progress_percent "DEPRECATED"
        varchar epub_progress "DEPRECATED"
        float epub_progress_percent "DEPRECATED"
        int cbx_progress "DEPRECATED"
        float cbx_progress_percent "DEPRECATED"
        varchar koreader_progress "1000 chars"
        float koreader_progress_percent
        varchar koreader_device "100 chars"
        varchar koreader_device_id "100 chars"
        timestamp koreader_last_sync_time
        float kobo_progress_percent
        varchar kobo_location "1000 chars"
        varchar kobo_location_type "50 chars"
        varchar kobo_location_source "512 chars"
        timestamp kobo_progress_received_time
        timestamp read_status_modified_time
    }

    user_book_file_progress {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_file_id FK "not null → book_file"
        varchar position_data "1000 chars"
        varchar position_href "1000 chars"
        float progress_percent
        varchar tts_position_cfi "1000 chars"
        timestamp last_read_time
    }

    reading_sessions {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        enum book_type "not null"
        timestamp start_time "not null"
        timestamp end_time "not null"
        int duration_seconds "not null"
        varchar duration_formatted
        float start_progress
        float end_progress
        float progress_delta
        varchar start_location "500 chars"
        varchar end_location "500 chars"
        datetime created_at
    }

    users ||--o{ user_book_progress : "tracks"
    book ||--o{ user_book_progress : "tracked by"
    users ||--o{ user_book_file_progress : "tracks"
    book_file ||--o{ user_book_file_progress : "tracked by"
    users ||--o{ reading_sessions : "has"
    book ||--o{ reading_sessions : "has"

    %% ═══════════════════════════════════
    %% ANNOTATIONS & NOTES
    %% ═══════════════════════════════════

    annotations {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        varchar cfi "1000 chars, not null"
        varchar text "5000 chars, not null"
        varchar color "20 chars"
        varchar style "50 chars"
        varchar note "5000 chars"
        varchar chapter_title "500 chars"
        bigint version "optimistic lock"
        datetime created_at
        datetime updated_at
    }

    book_notes {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        varchar title
        text content "not null"
        datetime created_at
        datetime updated_at
    }

    book_notes_v2 {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        varchar cfi "1000 chars, not null"
        varchar selected_text "5000 chars"
        text note_content "not null"
        varchar color "20 chars"
        varchar chapter_title "500 chars"
        bigint version "optimistic lock"
        datetime created_at
        datetime updated_at
    }

    book_marks {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        varchar cfi "1000 chars (EPUB)"
        bigint position_ms "audiobook ms"
        int track_index "audiobook track"
        varchar title
        varchar color
        varchar notes "2000 chars"
        int priority
        bigint version "optimistic lock"
        datetime created_at
        datetime updated_at
    }

    pdf_annotations {
        bigint id PK
        bigint user_id FK "not null → users"
        bigint book_id FK "not null → book"
        longtext data "JSON blob"
        bigint version "optimistic lock"
        datetime created_at
        datetime updated_at
    }

    users ||--o{ annotations : "creates"
    book ||--o{ annotations : "has"
    users ||--o{ book_notes : "creates"
    book ||--o{ book_notes : "has"
    users ||--o{ book_notes_v2 : "creates"
    book ||--o{ book_notes_v2 : "has"
    users ||--o{ book_marks : "creates"
    book ||--o{ book_marks : "has"
    users ||--o{ pdf_annotations : "creates"
    book ||--o{ pdf_annotations : "has"

    %% ═══════════════════════════════════
    %% VIEWER PREFERENCES
    %% ═══════════════════════════════════

    epub_viewer_preference {
        bigint id PK
        bigint user_id "not null"
        bigint book_id "not null"
        varchar theme
        varchar font
        int font_size
        float letter_spacing
        float line_height
        varchar flow
        varchar spread
        bigint custom_font_id FK "→ custom_font"
    }

    ebook_viewer_preference {
        bigint id PK
        bigint user_id "not null"
        bigint book_id "not null"
        varchar font_family "not null"
        int font_size "not null"
        float gap "not null"
        boolean hyphenate "not null"
        boolean is_dark "not null"
        boolean justify "not null"
        float line_height "not null"
        int max_block_size "not null"
        int max_column_count "not null"
        int max_inline_size "not null"
        varchar theme "not null"
        varchar flow "not null"
    }

    new_pdf_viewer_preference {
        bigint id PK
        bigint user_id "not null"
        bigint book_id "not null"
        enum spread
        enum view_mode
        enum fit_mode
        enum scroll_mode
        enum background_color
    }

    cbx_viewer_preference {
        bigint id PK
        bigint user_id "not null"
        bigint book_id "not null"
        enum spread
        enum view_mode
        enum fit_mode
        enum scroll_mode
        enum background_color
    }

    custom_font {
        bigint id PK
        bigint user_id FK "not null → users"
        varchar font_name "not null"
        varchar file_name UK "not null"
        varchar original_file_name "not null"
        enum format "not null"
        bigint file_size "not null"
        datetime uploaded_at "not null"
    }

    users ||--o{ custom_font : "uploads"

    %% ═══════════════════════════════════
    %% EXTERNAL INTEGRATIONS
    %% ═══════════════════════════════════

    koreader_user {
        bigint id PK
        bigint booklore_user_id FK "→ users"
        varchar username UK "100 chars, not null"
        varchar password "not null"
        varchar password_md5 "not null"
        timestamp created_at
        timestamp updated_at
        boolean sync_enabled "default false"
        boolean sync_with_booklore_reader "default false"
    }

    opds_user_v2 {
        bigint id PK
        bigint user_id FK "not null → users"
        varchar username "100 chars, not null"
        varchar password_hash "not null"
        enum sort_order "default RECENT"
        timestamp created_at
        timestamp updated_at
    }

    kobo_user_settings {
        bigint id PK
        bigint user_id UK "not null"
        varchar token "2048 chars, not null"
        boolean sync_enabled "default true"
        float progress_mark_as_reading_threshold "default 1"
        float progress_mark_as_finished_threshold "default 99"
        boolean auto_add_to_shelf "default false"
        varchar hardcover_api_key "2048 chars"
        boolean hardcover_sync_enabled "default false"
        boolean two_way_progress_sync "default false"
    }

    kobo_reading_state {
        bigint id PK
        bigint user_id
        varchar entitlement_id "not null"
        varchar created
        varchar last_modified
        varchar priority_timestamp
        json current_bookmark_json
        json statistics_json
        json status_info_json
    }

    kobo_library_snapshot {
        varchar id PK "UUID"
        bigint user_id "not null"
        datetime created_date "not null"
    }

    kobo_library_snapshot_book {
        bigint id PK
        varchar snapshot_id FK "not null → kobo_library_snapshot"
        bigint book_id "not null"
        varchar file_hash
        timestamp metadata_updated_at
        boolean synced "default false"
    }

    kobo_removed_books_tracking {
        bigint id PK
        varchar snapshot_id "not null"
        bigint user_id "not null"
        bigint book_id_synced "not null"
    }

    users ||--o| koreader_user : "has"
    users ||--o{ opds_user_v2 : "has"
    kobo_library_snapshot ||--o{ kobo_library_snapshot_book : "contains"

    %% ═══════════════════════════════════
    %% EMAIL
    %% ═══════════════════════════════════

    email_provider_v2 {
        bigint id PK
        bigint user_id "not null"
        varchar name UK "not null"
        varchar host "not null"
        int port "not null"
        varchar username "not null"
        varchar password "encrypted, not null"
        varchar from_address
        boolean auth "not null"
        boolean start_tls "not null"
        boolean is_default "not null"
        boolean shared "not null"
    }

    email_recipient_v2 {
        bigint id PK
        bigint user_id "not null"
        varchar email UK "not null"
        varchar name "not null"
        boolean is_default "not null"
    }

    user_email_provider_preference {
        bigint id PK
        bigint user_id UK "not null"
        bigint default_provider_id FK "→ email_provider_v2"
    }

    %% ═══════════════════════════════════
    %% ADMIN & SYSTEM
    %% ═══════════════════════════════════

    app_settings {
        bigint id PK
        varchar name "not null"
        text val "not null"
    }

    app_migration {
        varchar migration_key PK
        datetime executed_at "not null"
        varchar description
    }

    audit_log {
        bigint id PK
        bigint user_id
        varchar username "not null"
        enum action "not null"
        varchar entity_type "100 chars"
        bigint entity_id
        varchar description "1024 chars"
        varchar ip_address "45 chars (IPv6)"
        varchar country_code "2 chars"
        datetime created_at
    }

    tasks {
        varchar id PK "UUID"
        enum type "not null"
        enum status "not null"
        bigint user_id "not null"
        datetime created_at "not null"
        datetime updated_at
        datetime completed_at
        int progress_percentage
        varchar message "512 chars"
        text error_details
        text task_options "JSON"
    }

    task_cron_configuration {
        bigint id PK
        enum task_type UK "not null"
        varchar cron_expression "not null"
        boolean enabled "not null"
        bigint created_by "→ users"
        datetime created_at
        datetime updated_at
    }

    metadata_fetch_jobs {
        varchar task_id PK
        bigint user_id
        enum status "not null"
        text status_message
        timestamp started_at "not null"
        timestamp completed_at
        int total_books_count
        int completed_books
    }

    metadata_fetch_proposals {
        bigint proposal_id PK
        varchar task_id FK "→ metadata_fetch_jobs"
        bigint book_id "not null"
        timestamp fetched_at
        timestamp reviewed_at
        bigint reviewer_user_id
        enum status "not null"
        json metadata_json
    }

    bookdrop_file {
        bigint id PK
        text file_path "not null"
        varchar file_name "512 chars, not null"
        bigint file_size
        enum status "PENDING_REVIEW / FINALIZED"
        json original_metadata
        json fetched_metadata
        timestamp created_at
        timestamp updated_at
    }

    metadata_fetch_jobs ||--o{ metadata_fetch_proposals : "has"
```

---

## Key Design Patterns

| Pattern | Where | Notes |
|---------|-------|-------|
| Cascade ALL + OrphanRemoval | User→Shelves, Book→Files, Book→Metadata | Parent owns lifecycle of children |
| Optimistic Locking (`version`) | annotations, book_notes_v2, book_marks, pdf_annotations | Prevents concurrent edit conflicts |
| Metadata Locking | book_metadata (40+ `*_locked` booleans) | Per-field edit protection |
| Dynamic Update | book_metadata, comic_metadata | Only changed columns in SQL UPDATE |
| Join Tables (M:N) | authors, categories, moods, tags, shelves, libraries | Standard junction tables |
| Multi-system Progress | user_book_progress | Deprecated inline + KoReader + Kobo columns |
| Per-file Progress | user_book_file_progress | New system replacing deprecated inline columns |
| JSON Columns | magic_shelf.filter_json, bookdrop_file.metadata, tasks.task_options | Flexible schema-less data |
| Soft Delete | book.deleted + book.deleted_at | Books not physically removed |

---

## Enums Reference

| Enum | Values |
|------|--------|
| ReadStatus | UNREAD, READING, READ, DNF |
| BookFileType | EPUB, PDF, CBX, MOBI, AUDIOBOOK, AZW3, KEPUB, KFX |
| ProvisioningMethod | LOCAL, OIDC, REMOTE |
| IconType | MATERIAL, CUSTOM |
| MetadataSource | EMBEDDED, ONLINE |
| LibraryOrganizationMode | AUTO_DETECT, FLAT, HIERARCHICAL |
| FontFormat | TTF, OTF, WOFF, WOFF2 |
| ContentRestrictionType | AGE_RATING, CONTENT_RATING |
| ContentRestrictionMode | HIDE, BLUR |

---

## Table Count Summary

| Group | Tables | Notes |
|-------|--------|-------|
| Users & Auth | 7 | users, permissions, refresh_token, jwt_secret, oidc_session, oidc_group_mapping, user_settings |
| Libraries | 3 | library, library_path, user_library_mapping |
| Books & Files | 2 | book, book_file |
| Metadata | 10 | book_metadata, author, category, mood, tag, public_book_review + 4 join tables |
| Comics | 6 | comic_metadata, character, team, location, creator, creator_mapping + 3 join tables |
| Shelves | 3 | shelf, magic_shelf, book_shelf_mapping |
| Progress | 3 | user_book_progress, user_book_file_progress, reading_sessions |
| Annotations | 5 | annotations, book_notes, book_notes_v2, book_marks, pdf_annotations |
| Viewer Prefs | 5 | epub, ebook, pdf (legacy), new_pdf, cbx viewer preferences + custom_font |
| Integrations | 7 | koreader_user, opds_user_v2, kobo_user_settings, kobo_reading_state, kobo_library_snapshot, snapshot_book, removed_books |
| Email | 3 | email_provider_v2, email_recipient_v2, user_email_provider_preference |
| Admin | 7 | app_settings, app_migration, audit_log, tasks, task_cron, metadata_fetch_jobs, metadata_fetch_proposals, bookdrop_file |
| **Total** | **~60+** | |
