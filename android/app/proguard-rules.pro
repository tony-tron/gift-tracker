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

# Keep Room, WorkManager, and Glance constructors instantiated via reflection under R8 full mode
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.work.InputMerger {
    <init>();
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep public class * extends androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
