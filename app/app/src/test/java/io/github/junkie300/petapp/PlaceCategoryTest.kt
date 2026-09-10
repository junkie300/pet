package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCategoryTest {

    /**
     * dbValue 는 places.category ENUM 과 글자까지 같아야 한다. 하나라도 어긋나면
     * 필터가 조용히 0건을 돌려주고, 화면은 "이 동네엔 없습니다"로 읽힌다.
     * ENUM 정의는 supabase/migrations/20260830090200_places.sql 에 있다.
     */
    @Test
    fun `dbValue 가 DB ENUM 과 일치한다`() {
        assertEquals(
            listOf("hospital", "grooming", "restaurant", "tour", "wildlife_center"),
            PlaceCategory.entries.map { it.dbValue },
        )
    }

    @Test
    fun `모르는 값은 null 이다`() {
        // 화면 간에 실려 오는 문자열이다. 예전 링크나 오타가 앱을 죽이면 안 된다.
        assertNull(PlaceCategory.fromDbValue("cafe"))
        assertNull(PlaceCategory.fromDbValue(null))
        assertEquals(PlaceCategory.HOSPITAL, PlaceCategory.fromDbValue("hospital"))
    }

    /**
     * 적재 안 된 카테고리는 질의도 하지 않는다 — 0건이 "이 동네엔 없다"로 읽히면 안 되기 때문이다.
     * 단계별 ETL 이 끝날 때마다 이 목록이 늘어난다.
     */
    @Test
    fun `적재된 카테고리만 조회 대상이다`() {
        assertEquals(
            listOf(PlaceCategory.HOSPITAL, PlaceCategory.GROOMING),
            PlaceCategory.loadedEntries,
        )
        assertTrue(PlaceCategory.entries.filterNot { it.loaded }.isNotEmpty())
    }
}
