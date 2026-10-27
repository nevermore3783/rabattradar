# kotlinx.serialization: keep generated serializers of the snapshot model (cached as JSON on disk).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class at.rabattradar.core.** {
    *** Companion;
}
-keepclasseswithmembers class at.rabattradar.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class at.rabattradar.core.**$$serializer { *; }

# OkHttp optional platform integrations.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
