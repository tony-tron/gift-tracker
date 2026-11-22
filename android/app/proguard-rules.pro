# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class com.tonytron.gifttracker.data.model.** {
    *;
}
-keep,includedescriptorclasses class com.tonytron.gifttracker.data.model.**$$serializer { *; }

# Keep Firestore reflection / model mapping safe
-keep class com.google.firebase.** { *; }
-dontwarn com.google.appengine.**
-dontwarn io.grpc.**
