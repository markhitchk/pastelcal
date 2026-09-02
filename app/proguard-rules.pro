# PastelCal keeps no reflective application model outside AndroidX/Room generated code.
# Keep Room database implementations and entity metadata conservative for release builds.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
