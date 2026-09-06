package io.github.junkie300.petapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.junkie300.petapp.data.cache.CacheDao
import io.github.junkie300.petapp.data.cache.CachedCount
import io.github.junkie300.petapp.data.cache.CachedPlace
import io.github.junkie300.petapp.data.cache.CachedRegion
import io.github.junkie300.petapp.data.favorite.FavoriteDao
import io.github.junkie300.petapp.data.favorite.FavoritePlace

/**
 * 앱 로컬 DB — **사용자가 만든 것**(즐겨찾기)과 **서버에서 받아 둔 사본**(오프라인 캐시).
 *
 * 둘을 한 파일에 두되 섞지는 않는다. 즐겨찾기는 지우지 않고, 캐시는 상한을 넘으면 밀어낸다
 * ([CacheDao.MAX_CACHED_PLACES]). 공공데이터 전체를 여기 내려받지는 않는다 — 그건 캐시가
 * 아니라 사본이고, 기준일이 낡는 순간 출처 표기(spec.md §5.2)가 거짓이 된다.
 *
 * `exportSchema = true` 로 둔다. 스키마 JSON 이 있어야 나중에 버전을 올릴 때 마이그레이션을
 * 쓸 수 있고, 없으면 "이전 스키마를 모른다"는 상태가 된다.
 */
@Database(
    entities = [FavoritePlace::class, CachedRegion::class, CachedPlace::class, CachedCount::class],
    version = 2,
    exportSchema = true,
)
abstract class PetDatabase : RoomDatabase() {

    abstract fun favorites(): FavoriteDao

    abstract fun cache(): CacheDao

    companion object {

        /**
         * 1 → 2. 오프라인 캐시 표 3개를 더하고, 즐겨찾기 스냅샷에 **출처·기준일**을 더한다.
         *
         * ⚠️ **`fallbackToDestructiveMigration()` 을 쓰지 않는다.** 즐겨찾기는 사용자가
         * 직접 담은 것이고 서버에 사본이 없다(로그인이 없다). 버전을 올렸다고 날리면
         * 복구할 방법이 아예 없다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach(db::execSQL)
            }
        }

        fun create(context: Context): PetDatabase =
            Room.databaseBuilder(context, PetDatabase::class.java, "pet.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * 1 → 2 마이그레이션이 실행하는 문장 전부.
 *
 * ⚠️ 이 문장들은 Room 이 **새로 깐 기기에서 만드는 표와 한 글자도 다르면 안 된다.** 다르면
 * 앱을 켤 때 `IllegalStateException: Migration didn't properly handle...` 이 난다. 눈으로
 * 맞추지 말 것 — `MigrationSqlTest` 가 `schemas/.../2.json` 과 자동으로 대조한다.
 * 그래서 마이그레이션 본문이 아니라 **읽을 수 있는 목록**으로 빼 두었다.
 */
internal val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `cached_regions` (`code` TEXT NOT NULL, `level` INTEGER NOT NULL, " +
        "`parent_code` TEXT, `full_name` TEXT NOT NULL, `sido_name` TEXT NOT NULL, " +
        "`sigungu_name` TEXT, `dong_name` TEXT, `center_lat` REAL, `center_lng` REAL, " +
        "`cached_at` INTEGER NOT NULL, PRIMARY KEY(`code`))",
    "CREATE INDEX IF NOT EXISTS `index_cached_regions_parent_code` ON `cached_regions` (`parent_code`)",
    "CREATE TABLE IF NOT EXISTS `cached_places` (`place_id` INTEGER NOT NULL, `region_code` TEXT, " +
        "`category` TEXT NOT NULL, `source` TEXT NOT NULL, `name` TEXT NOT NULL, `tel` TEXT, " +
        "`address_road` TEXT, `address_jibun` TEXT, `lat` REAL, `lng` REAL, `status` TEXT NOT NULL, " +
        "`extra` TEXT, `source_updated_at` TEXT, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`place_id`))",
    "CREATE INDEX IF NOT EXISTS `index_cached_places_region_code` ON `cached_places` (`region_code`)",
    "CREATE TABLE IF NOT EXISTS `cached_counts` (`region_code` TEXT NOT NULL, `category` TEXT NOT NULL, " +
        "`count` INTEGER NOT NULL, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`region_code`, `category`))",

    // 즐겨찾기 스냅샷에 더하는 칸. **순서는 FavoritePlace 에 선언한 순서 그대로다.**
    // 값은 채우지 않는다 — 옛 즐겨찾기는 출처를 모르며, 모르는 것을 지어내지 않는다.
    "ALTER TABLE `favorite_places` ADD COLUMN `source` TEXT",
    "ALTER TABLE `favorite_places` ADD COLUMN `address_road` TEXT",
    "ALTER TABLE `favorite_places` ADD COLUMN `address_jibun` TEXT",
    "ALTER TABLE `favorite_places` ADD COLUMN `lat` REAL",
    "ALTER TABLE `favorite_places` ADD COLUMN `lng` REAL",
    "ALTER TABLE `favorite_places` ADD COLUMN `status` TEXT",
    "ALTER TABLE `favorite_places` ADD COLUMN `extra` TEXT",
    "ALTER TABLE `favorite_places` ADD COLUMN `source_updated_at` TEXT",
)
