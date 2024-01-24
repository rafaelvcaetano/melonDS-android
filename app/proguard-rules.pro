# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepclassmembers enum * { *; }

-keep class me.magnum.melonds.domain.model.RendererConfiguration { *; }
-keep class me.magnum.melonds.domain.model.FirmwareConfiguration { *; }
-keep class me.magnum.melonds.domain.model.EmulatorConfiguration { *; }
-keep class me.magnum.melonds.domain.model.AudioBitrate { *; }
-keep class me.magnum.melonds.domain.model.AudioInterpolation { *; }
-keep class me.magnum.melonds.domain.model.AudioLatency { *; }
-keep class me.magnum.melonds.domain.model.ConsoleType { *; }
-keep class me.magnum.melonds.domain.model.MicSource { *; }
-keep class me.magnum.melonds.domain.model.Cheat { *; }
-keep class me.magnum.melonds.domain.model.DSiWareTitle { *; }
-keep class me.magnum.melonds.domain.model.VideoRenderer { *; }
-keep class me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement { *; }
-keep class me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState { *; }
-keep class me.magnum.melonds.ui.emulator.rewind.model.RewindWindow { *; }
-keep class me.magnum.melonds.ui.settings.fragments.**
-keep class me.magnum.melonds.common.UriFileHandler {
    public int open(java.lang.String, java.lang.String);
}
-keep interface me.magnum.melonds.common.camera.DSiCameraSource { *; }
-keep interface me.magnum.melonds.common.RetroAchievementsCallback { *; }

# Migration fields. These rules are required for migrations to work properly
-keep,allowobfuscation class me.magnum.melonds.migrations.legacy.** { *; }

# Prevent DTOs from being removed
-keep,allowobfuscation class me.magnum.melonds.impl.dtos.**
-keep,allowobfuscation class me.magnum.rcheevosapi.dto.**

# Prevent Gson's TypeToken from being removed
-keepattributes Signature
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken