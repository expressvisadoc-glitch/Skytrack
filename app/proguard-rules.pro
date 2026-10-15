# Proguard / R8 rules for SkyTrack Android App

# Keep all data models used by Gson & Retrofit serialization/deserialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.skypass.skytrack.data.remote.** { *; }
-keepclassmembers class com.skypass.skytrack.data.remote.** { *; }

-keep class com.skypass.skytrack.data.local.** { *; }
-keepclassmembers class com.skypass.skytrack.data.local.** { *; }

-keep class com.skypass.skytrack.data.repository.** { *; }
-keepclassmembers class com.skypass.skytrack.data.repository.** { *; }

-keep class com.skypass.skytrack.data.update.** { *; }
-keepclassmembers class com.skypass.skytrack.data.update.** { *; }

# Keep Room database, entities, and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Keep Retrofit interfaces
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Preserve line numbers and source files for clean stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile