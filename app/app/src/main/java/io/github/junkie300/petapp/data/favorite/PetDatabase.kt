package io.github.junkie300.petapp.data.favorite

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 앱 로컬 DB. 지금은 즐겨찾기 하나뿐이다.
 *
 * 공공데이터(regions·places)는 여기 두지 않는다 — 그건 Supabase 가 갖고 있고,
 * 이 DB 는 **사용자가 만든 것**만 담는다. 오프라인 캐시가 붙으면 그때 테이블이 는다.
 *
 * `exportSchema = true` 로 둔다. 스키마 JSON 이 있어야 나중에 마이그레이션을 쓸 수 있고,
 * 없으면 버전을 올리는 순간 "이전 스키마를 모른다"는 상태가 된다.
 */
@Database(entities = [FavoritePlace::class], version = 1, exportSchema = true)
abstract class PetDatabase : RoomDatabase() {

    abstract fun favorites(): FavoriteDao

    companion object {
        fun create(context: Context): PetDatabase =
            Room.databaseBuilder(context, PetDatabase::class.java, "pet.db").build()
    }
}
