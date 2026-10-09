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
