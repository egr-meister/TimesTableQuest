# TimesTable Quest uses no reflection-based serialization. Room, DataStore and Compose ship their own
# consumer rules. Keep line numbers for readable crash traces (CI preserves mapping.txt when R8 is on).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
