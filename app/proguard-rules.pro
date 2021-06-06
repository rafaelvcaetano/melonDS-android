# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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

-dontobfuscate

-keepclassmembers enum * { *; }

-keep class me.magnum.melonds.domain.model.RendererConfiguration { *; }
-keep class me.magnum.melonds.domain.model.FirmwareConfiguration { *; }
-keep class me.magnum.melonds.domain.model.EmulatorConfiguration { *; }
-keep class me.magnum.melonds.domain.model.ConsoleType { *; }
-keep class me.magnum.melonds.domain.model.MicSource { *; }
-keep class me.magnum.melonds.domain.model.Cheat { *; }
-keep class me.magnum.melonds.ui.settings.CustomFirmwarePreferencesFragment { *; }
-keep class me.magnum.melonds.ui.settings.FirmwarePreferencesFragment { *; }
-keep class me.magnum.melonds.common.UriFileHandler {
    public int open(java.lang.String, java.lang.String);
}
