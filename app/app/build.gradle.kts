import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase 접속 정보는 local.properties 에서 읽는다 (커밋되지 않는다).
// CI 에서는 같은 이름의 환경변수로 넘긴다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: "").trim()

/** legacy JWT 키에서 role 클레임을 읽는다. JWT 가 아니면 null (새 형식은 판별할 수 없다). */
fun jwtRole(key: String): String? {
    val parts = key.split(".")
    if (parts.size != 3) return null
    return runCatching {
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))
        payload.substringAfter(""""role":"""", "").substringBefore('"').ifBlank { null }
    }.getOrNull()
}

// service_role 키는 RLS 를 우회한다. APK 에 들어가면 누구나 DB 를 쓸 수 있게 된다.
// etl/petetl/db.py 가 반대 방향(anon 을 ETL 에 넣는 것)을 막는 것과 같은 이유의 방어다.
// 이쪽이 더 위험하다 — ETL 은 실패로 끝나지만, 이건 공개된 APK 안에 남는다.
val anonKey = secret("SUPABASE_ANON_KEY")
val anonKeyRole = jwtRole(anonKey)
if (anonKeyRole != null && anonKeyRole != "anon") {
    throw GradleException(
        """
        SUPABASE_ANON_KEY 에 role='$anonKeyRole' 키가 들어 있습니다. 앱에는 'anon' 키만 넣습니다.
          → 이 키는 APK 안에 그대로 박혀서 배포됩니다. service_role 이면 DB 가 열립니다.
          → Supabase 대시보드 > Project Settings > API Keys > Legacy API keys 에서
            'anon' (public 이라고 표시된 쪽) 키로 바꾸세요.
          → 'service_role' (secret) 키는 ETL 전용이며 etl/.env 에만 둡니다.
        """.trimIndent(),
    )
}

android {
    namespace = "io.github.junkie300.petapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.junkie300.petapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // anon 키는 앱에 노출되는 공개 키다. RLS 로 읽기 전용이 강제되어 있어야 한다.
        // service_role 키는 절대 여기 넣지 않는다 (spec.md §1.2).
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
