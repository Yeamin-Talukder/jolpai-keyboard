# Lipi release shrinking rules.
# Hilt, Room, Compose and kotlinx-serialization ship their own consumer rules.
# Lipi parses JSON only through the JsonElement tree (no reflection), and loads its word lists,
# Avro rules and emoji data as Java resources, which R8 keeps.

# Keep line numbers for readable stack traces from user reports (no source file names leaked).
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile

# SQLCipher (encrypted database): JNI calls into these classes by name.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ML Kit on-device translation. ML Kit finds its parts at runtime by class name (component
# registrars listed in the manifest) and its native library calls back into Java classes by
# name, so R8 must not rename or strip them: with them shrunk, RemoteModelManager.getInstance()
# returned null and the translate box crashed the keyboard (2026-11-07).
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
