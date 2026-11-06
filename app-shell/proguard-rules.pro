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
