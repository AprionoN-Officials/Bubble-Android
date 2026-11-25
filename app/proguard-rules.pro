# ML Kit ships its own consumer ProGuard rules for JNI/reflection entry points.
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Keep Tesseract4Android & Leptonica JNI bridge classes
-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }
-dontwarn com.googlecode.tesseract.**
-dontwarn com.googlecode.leptonica.**

# Keep Android service & provider subclasses declared in manifest
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.service.quicksettings.TileService
-keep public class * extends android.service.voice.VoiceInteractionService
-keep public class * extends android.service.voice.VoiceInteractionSessionService
-keep public class * extends android.speech.RecognitionService
-keep public class * extends android.app.Service
-keep public class * extends androidx.core.content.FileProvider

# Strip verbose debug logs in release APK
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
