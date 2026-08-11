# smbj
-keep class com.hierynomus.** { *; }
-keep class com.hierynomus.smbj.** { *; }
-keep class org.bouncycastle.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Media3
-keep class androidx.media3.** { *; }

# Media3 FFmpeg 扩展解码器
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# jmdns (mDNS discovery, uses reflection)
-keep class javax.jmdns.** { *; }
-keep class org.jmdns.** { *; }

# jaudiotagger (audio metadata, uses reflection)
-keep class org.jaudiotagger.** { *; }

# jaudiotagger 引用的 AWT/ImageIO/EL 类在 Android 平台不存在（运行不会触达），抑制 R8 缺类警告
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.filechooser.**
-dontwarn javax.el.**

# smbj/jcifs 引用的 GSS（Kerberos）类在 Android 平台不存在，抑制 R8 缺类警告
-dontwarn org.ietf.jgss.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.** { *; }
