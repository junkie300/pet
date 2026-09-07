package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Fetched
import io.github.junkie300.petapp.data.cachedThenFresh
import io.github.junkie300.petapp.data.fetchOrCached
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 사본을 먼저 그리고 뒤에서 갱신한다 (cache-first — D-79).
 *
 * 규칙은 두 줄이다.
 * 1. 사본이 있으면 **즉시** 내보낸다. 단 **배너는 안 단다** — 낡았는지는 서버가 답해 봐야 안다 (D-66).
 * 2. 서버가 못 주면 그때 같은 사본을 **배너를 달아** 다시 내보낸다.
 */
class CacheFirstTest {

    private val copy = Fetched("사본", cachedAt = 1_000L)
    private val boom = IOException("圈外")

    @Test
    fun `사본이 있으면 먼저 내보내고 서버 답으로 갈아 끼운다`() = runTest {
        val stored = mutableListOf<String>()

        val emitted = cachedThenFresh(
            remote = { "서버" },
            store = { stored += it },
            cached = { copy },
        ).toList()

        assertEquals(listOf("사본", "서버"), emitted.map { it.data })
        // ⚠️ 첫 값은 사본이지만 **배너를 달지 않는다.** 網이 멀쩡한데 "오프라인"이 깜빡이면 안 된다.
        assertNull("서버를 기다리는 중에는 배너가 없다", emitted[0].offlineSince)
        assertTrue(emitted[0].awaitingServer)
        // 두 번째는 방금 받은 값이다. 사본으로도 남는다.
        assertNull(emitted[1].offlineSince)
        assertEquals(listOf("서버"), stored)
    }

    @Test
    fun `서버가 못 주면 그때 사본에 배너가 붙는다`() = runTest {
        val emitted = cachedThenFresh<String>(
            remote = { throw boom },
            store = { },
            cached = { copy },
        ).toList()

        assertEquals(listOf("사본", "사본"), emitted.map { it.data })
        assertNull("아직 모를 때는 배너가 없다", emitted[0].offlineSince)
        assertEquals("서버가 못 준 것이 밝혀진 뒤에 배너가 뜬다", 1_000L, emitted[1].offlineSince)
    }

    /**
     * ⚠️ 사본이 없으면 실패는 실패다. 빈 값을 성공으로 돌려주면 화면이 "이 동네엔 없습니다"를
     * 그리는데, 우리는 없다는 것을 안 게 아니라 **모르는 것**이다 (spec.md §5.3).
     */
    @Test
    fun `사본도 서버도 없으면 실패다`() = runTest {
        val thrown = runCatching {
            cachedThenFresh<String>(remote = { throw boom }, store = { }, cached = { null }).toList()
        }.exceptionOrNull()

        assertTrue("서버 예외가 그대로 올라와야 한다", thrown is IOException)
        assertEquals("圈外", thrown?.message)
    }

    @Test
    fun `사본이 없으면 서버 값 하나만 흐른다`() = runTest {
        val emitted = cachedThenFresh(remote = { "서버" }, store = { }, cached = { null }).toList()

        assertEquals(listOf(Fetched("서버")), emitted)
    }

    /** 사본을 남기다 실패해도 **화면은 살아야 한다.** 방금 받은 값은 멀쩡하다. */
    @Test
    fun `사본을 남기지 못해도 받은 값은 흘러간다`() = runTest {
        val emitted = cachedThenFresh(
            remote = { "서버" },
            store = { throw IllegalStateException("디스크 꽉 참") },
            cached = { null },
        ).toList()

        assertEquals(listOf("서버"), emitted.map { it.data })
    }

    /** 캐시는 편의지 관문이 아니다. 사본을 꺼내다 깨져도 서버에는 물어봐야 한다. */
    @Test
    fun `사본을 꺼내다 깨져도 서버 조회는 계속한다`() = runTest {
        val emitted = cachedThenFresh(
            remote = { "서버" },
            store = { },
            cached = { throw IllegalStateException("DB 손상") },
        ).toList()

        assertEquals(listOf("서버"), emitted.map { it.data })
    }

    /**
     * 값 하나만 필요한 곳(S-01 드롭다운·검색)은 예전 그대로다 — **마지막 값이 답이다.**
     * 사본이 앞에 흘렀다고 해서 옛 값을 답으로 주면 안 된다.
     */
    @Test
    fun `값 하나만 필요한 곳은 마지막 값을 받는다`() = runTest {
        assertEquals(
            Fetched("서버"),
            fetchOrCached(remote = { "서버" }, store = { }, cached = { copy }),
        )
        assertEquals(
            copy,
            fetchOrCached<String>(remote = { throw boom }, store = { }, cached = { copy }),
        )
    }
}
