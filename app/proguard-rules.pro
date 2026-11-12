# Regras R8 do release. AndroidX, Material e Coroutines já trazem as suas.

# OpenCV: classes Java chamadas via JNI pela lib nativa; não renomear/remover.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# junrar (CBR/RAR)
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**
# Dependências opcionais referenciadas por junrar/slf4j que não vão no APK
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.vfs2.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# SubsamplingScaleImageView instancia os decoders por reflexão (Class.newInstance)
-keep class * implements com.davemorrissey.labs.subscaleview.decoder.ImageDecoder { <init>(...); }
-keep class * implements com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder { <init>(...); }
