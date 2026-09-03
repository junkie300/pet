# kotlinx.serialization 이 생성한 serializer 를 R8 이 지우지 않도록 남긴다.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.junkie300.petapp.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.junkie300.petapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
