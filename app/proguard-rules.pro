# KeyBox ProGuard/R8 混淆规则

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ===== BouncyCastle（Argon2id 反射调用） =====
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ===== Kotlin 协程 =====
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ===== Compose =====
-dontwarn androidx.compose.**

# ===== 序列化/JSON =====
-keep class org.json.** { *; }

# 保留数据实体（KSP 生成的 DAO 依赖）
-keep class com.keybox.app.data.db.** { *; }

# ===== Apache POI（Excel 解析，反射 + ServiceLoader） =====
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.collections4.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.codec.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class com.graphbuilder.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.codec.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**
-dontwarn org.slf4j.**
-dontwarn javax.xml.stream.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.xmlgraphics.**
