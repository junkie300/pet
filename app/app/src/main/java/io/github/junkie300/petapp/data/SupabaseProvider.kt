package io.github.junkie300.petapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.junkie300.petapp.BuildConfig

/**
 * Supabase 클라이언트 하나를 앱 전체가 공유한다.
 *
 * anon 키는 APK 안에 그대로 들어간다. 이건 설계상 그렇게 되는 것이고, 안전은
 * RLS(읽기 전용 정책)가 보장한다 — spec.md §1.2 / §8. service_role 키는 절대 넣지 않는다.
 */
object SupabaseProvider {

    /** local.properties(또는 CI 환경변수)가 비어 있으면 false. 화면에서 안내로 구분해 보여준다. */
    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) { "SUPABASE_URL / SUPABASE_ANON_KEY 가 비어 있습니다." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Postgrest)
        }
    }
}
