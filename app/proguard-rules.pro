# Readium
-keep class org.readium.r2.shared.publication.** { *; }
-keep class org.readium.r2.navigator.** { *; }
-keep class org.readium.r2.streamer.** { *; }

# Ktor
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class com.ember.reader.** {
    *;
}

# Room entities and converters
-keep class com.ember.reader.core.database.entity.** { *; }
-keep class com.ember.reader.core.database.converter.Converters { *; }

# Hilt
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# WorkManager + Hilt Worker
-keep class com.ember.reader.core.sync.worker.SyncWorker { *; }

# Coil
-dontwarn coil.**
