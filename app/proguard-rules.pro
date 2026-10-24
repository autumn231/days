# ---------------------------------------------------------------------------
# 倒数日 release R8 规则
# 开启 isMinifyEnabled 后需要保留 Room 依赖的符号，否则运行时会崩。
# ---------------------------------------------------------------------------

# Room：实体 / DAO 与生成实现通过反射按类名查找
-keep class com.example.countdowndays.data.** { *; }
-keepclassmembers class com.example.countdowndays.data.** { *; }

# Room 运行时通过类名反射实例化 <Database>_Impl
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Coil
-dontwarn coil.**

# 保留行号，便于定位线上崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
