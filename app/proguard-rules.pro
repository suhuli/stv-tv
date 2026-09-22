# 应用自身（含 kotlinx.serialization 生成的序列化器）
-keep class cx.n181.stv.** { *; }

# ijkplayer 通过 JNI 回调 Java 层
-keep class tv.danmaku.ijk.media.player.** { *; }
-keep interface tv.danmaku.ijk.media.player.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class cx.n181.stv.**$$serializer { *; }
-keepclassmembers class cx.n181.stv.** { *** Companion; }
-keepclasseswithmembers class cx.n181.stv.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
