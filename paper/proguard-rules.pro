# Add project specific ProGuard rules here.

# Security: Prevent R8 from obfuscating classes that need to be serialized/deserialized
-keepattributes *Annotation*, Signature, InnerClasses

# Keep serializable data classes for Kotlin Serialization
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# Targeted Keep rules for logic models (shared with :core, avoids "overly broad" warning)
-keep class com.masterclock.app.logic.PlayerSettings { *; }
-keep class com.masterclock.app.logic.ChessClockSettings { *; }
-keep class com.masterclock.app.logic.GameLog { *; }
-keep class com.masterclock.app.logic.GameEvent { *; }
-keep class com.masterclock.app.logic.NotebookNote { *; }
-keep class com.masterclock.app.logic.ScoreboardSession { *; }
-keep class com.masterclock.app.logic.ScoreboardGame { *; }

# Keep Room generated code and entities
-keep class com.masterclock.app.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# Keep Compose/Material specific requirements if needed by R8
-dontwarn androidx.compose.**
