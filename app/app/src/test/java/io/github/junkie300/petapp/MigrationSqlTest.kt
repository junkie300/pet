package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.local.MIGRATION_1_2_SQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **마이그레이션 SQL 이 Room 이 만드는 표와 같은지**를 기계로 대조한다.
 *
 * 이 트랩은 눈으로 못 잡는다. 한 글자만 달라도 앱은 빌드되고 테스트도 통과한 뒤,
 * **버전 1 을 깔아 둔 기기에서만** `IllegalStateException: Migration didn't properly handle`
 * 로 죽는다. 새로 깐 기기에서는 표를 새로 만들기 때문에 개발 중에는 드러나지 않는다.
 *
 * 그래서 `exportSchema = true` 로 남긴 JSON 을 정답으로 삼는다 (D-61).
 */
class MigrationSqlTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun entities(version: Int): List<Pair<String, List<String>>> {
        val file = File("schemas/$DATABASE/$version.json")
        assertTrue(
            "스키마 JSON 이 없다: ${file.absolutePath} — build.gradle.kts 의 room.schemaLocation 을 확인할 것",
            file.exists(),
        )
        val database = json.parseToJsonElement(file.readText()).jsonObject
            .getValue("database").jsonObject
        return database.getValue("entities").jsonArray.map { element ->
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            val statements = buildList {
                add(entity.getValue("createSql").jsonPrimitive.content)
                entity["indices"]?.jsonArray?.forEach { index ->
                    add(index.jsonObject.getValue("createSql").jsonPrimitive.content)
                }
            }.map { it.replace("\${TABLE_NAME}", table) }
            table to statements
        }
    }

    private fun columns(version: Int, table: String): Set<String> {
        val file = File("schemas/$DATABASE/$version.json")
        val entity = json.parseToJsonElement(file.readText()).jsonObject
            .getValue("database").jsonObject
            .getValue("entities").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("tableName").jsonPrimitive.content == table }
        return entity.getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
            .toSet()
    }

    /** 버전 2 에서 새로 생긴 표는 마이그레이션이 **똑같은 문장으로** 만들어야 한다. */
    @Test
    fun `새 표의 CREATE 문이 Room 이 만드는 것과 한 글자도 다르지 않다`() {
        val newTables = entities(2).map { it.first } - entities(1).map { it.first }.toSet()
        assertEquals(listOf("cached_regions", "cached_places", "cached_counts"), newTables)

        entities(2).filter { it.first in newTables }.forEach { (table, statements) ->
            statements.forEach { sql ->
                assertTrue("$table 의 문장이 마이그레이션에 없다:\n$sql", sql in MIGRATION_1_2_SQL)
            }
        }
    }

    /** 즐겨찾기에 더한 칸은 하나도 빠짐없이 ALTER 로 들어가야 한다 (D-61 의 출처·기준일). */
    @Test
    fun `즐겨찾기에 더한 칸이 전부 ALTER 로 들어간다`() {
        val added = columns(2, FAVORITES) - columns(1, FAVORITES)
        assertEquals(
            setOf("source", "address_road", "address_jibun", "lat", "lng", "status", "extra", "source_updated_at"),
            added,
        )
        added.forEach { column ->
            val prefix = "ALTER TABLE `$FAVORITES` ADD COLUMN `$column` "
            assertTrue("$column 을 더하는 ALTER 가 없다", MIGRATION_1_2_SQL.any { it.startsWith(prefix) })
        }
    }

    /**
     * ⚠️ **즐겨찾기 표를 다시 만들지 않는다.**
     * 사용자가 직접 담은 것이고 서버에 사본이 없다(로그인이 없다). 지우면 복구할 방법이 없다.
     */
    @Test
    fun `즐겨찾기 표를 지우거나 다시 만들지 않는다`() {
        assertTrue(
            MIGRATION_1_2_SQL.none { sql ->
                sql.startsWith("DROP") || (sql.startsWith("CREATE TABLE") && FAVORITES in sql)
            },
        )
    }

    private companion object {
        const val DATABASE = "io.github.junkie300.petapp.data.local.PetDatabase"
        const val FAVORITES = "favorite_places"
    }
}
