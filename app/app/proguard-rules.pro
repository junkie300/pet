# kotlinx.serialization 이 생성한 serializer 를 R8 이 지우지 않도록 남긴다.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.junkie300.petapp.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.junkie300.petapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 카카오맵 SDK 는 네이티브(C++)에서 자바 클래스·메서드를 **이름으로** 찾는다.
# AAR 에 consumer proguard 규칙이 들어 있지 않아서(실측 2.15.1), R8 이 이름을 바꾸면
# release 에서만 지도가 죽는다 — debug 에서는 절대 드러나지 않는다 (D-70).
-keep class com.kakao.vectormap.** { *; }
-keepclassmembers class com.kakao.vectormap.** {
    native <methods>;
}
