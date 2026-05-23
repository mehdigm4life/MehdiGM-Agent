-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.gs.agent.**$$serializer { *; }
-keepclassmembers class com.gs.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.gs.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
