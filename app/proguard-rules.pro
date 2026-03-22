# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# ML Kit
-keep class com.google.mlkit.** { *; }

# Security Crypto
-keep class androidx.security.crypto.** { *; }

# NafsShield models
-keep class com.nafsshield.data.model.** { *; }
-keep class com.nafsshield.util.** { *; }
