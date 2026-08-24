# PocketPad ProGuard Rules
# Keep Compose runtime
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep data classes used for serialization
-keepclassmembers class com.aistudio.pocketpad.rcvbwq.model.** { *; }

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep ZXing barcode scanner
-keep class com.google.zxing.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Suppress warnings for missing annotations
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
