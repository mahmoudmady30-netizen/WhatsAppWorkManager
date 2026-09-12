# Keep Room entities and DAOs
-keep class com.whatsappworkmanager.app.data.local.entity.** { *; }

# Keep model classes used with reflection-free JSON handling
-keepclassmembers class com.whatsappworkmanager.app.domain.model.** { *; }

# WorkManager
-keep class androidx.work.impl.WorkDatabase { *; }

# Do not warn about optional AI SDK reflection
-dontwarn okhttp3.**
-dontwarn org.json.**
