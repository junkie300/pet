package io.github.junkie300.petapp.ui.map

/**
 * 핀 옆에 적을 이름 (D-85).
 *
 * 이름 레이어는 겹침 경쟁(`CompetitionType.All`)을 켜 두었지만 **좌표가 똑같으면 경쟁이
 * 갈라 주지 못한다.** 역삼동의 `24시 온숲 동물의료센터` 와 `온숲 영상·외과 동물의료센터` 는
 * 좌표가 글자까지 같아서 두 이름이 같은 자리에 겹쳐 찍히고, 둘 다 읽을 수 없게 된다 (D-83 실측).
 *
 * ⚠️ **좌표가 달라도 갈라지지 않던 것은 다른 이유였다** (D-92 · 자양동 실측). 경쟁에 **순서가
 * 없어서**다 — 아래 [MapName.rank] 를 볼 것.
 *
 * 그래서 **같은 자리는 이름을 하나만 그린다.** 다만 나머지를 조용히 없애지는 않는다 —
 * 핀 하나에 장소가 둘이라는 사실은 `외 1` 로 밝히고, 무엇이 있는지는 시트의 목록이 말한다.
 * (핀을 지우거나 묶음 원으로 바꾸지 않는 이유: 아무리 당겨도 안 갈라지는 자리라
 * 눌러도 아무 일이 없는 원이 된다 — D-83 이 고친 그 증상이다.)
 */
data class MapName(
    val latitude: Double,
    val longitude: Double,
    /** 대표로 적을 이름. 목록 순서(이름순)에서 앞선 것이다. */
    val name: String,
    /** 같은 자리라 이름을 못 적은 나머지 수. 0이면 혼자다. */
    val hiddenCount: Int,
    /**
     * 겹쳤을 때 **누가 남는가** (D-92).
     *
     * ⚠️ **경쟁에는 순서가 있어야 한다.** 레이어의 `OrderingType` 기본값이 `Rank` 인데
     * `LabelOptions.rank` 는 아무도 안 건드리면 **전부 0** 이다 (SDK 2.15.1 을 뜯어 확인).
     * 그래서 좌표가 달라도 이름끼리는 갈라지지 않는다 — 우열이 없으면 둘 다 남는다.
     *
     * 앞선 것이 크다. **[hiddenCount] 의 대표를 고르는 규칙과 같은 순서**(목록의 이름순)라,
     * 같은 자리에서 남는 이름과 겹쳤을 때 남는 이름이 같은 기준을 따른다 (D-85).
     */
    val rank: Long,
)

/**
 * [clusters] 중 **핀 하나짜리**의 이름만 모은다. 여럿짜리 묶음은 개수를 적은 원이 대신하므로
 * 이름을 붙이지 않는다 (D-78).
 *
 * 좌표는 소수점까지 그대로 비교한다. 같은 데이터 원본에서 온 **글자까지 같은 좌표**가 문제이고,
 * 아주 가깝지만 다른 좌표는 겹침 경쟁이 갈라 준다([MapName.rank] 가 그 순서를 준다) —
 * 여기서 반올림해 묶으면 배율을 올려 떨어뜨려 놓아도 이름이 하나로 남는다.
 */
fun mapNames(clusters: List<MapCluster>): List<MapName> {
    val grouped = LinkedHashMap<Pair<Double, Double>, MutableList<MapPin>>()
    for (cluster in clusters) {
        val single = cluster.single ?: continue
        grouped.getOrPut(single.latitude to single.longitude) { mutableListOf() } += single
    }
    return grouped.entries.mapIndexed { index, (position, pins) ->
        MapName(
            latitude = position.first,
            longitude = position.second,
            name = pins.first().name,
            hiddenCount = pins.size - 1,
            // 앞선 것이 크다. 목록이 몇 개든 음수로 내려가지 않게 개수에서 뺀다.
            rank = (grouped.size - index).toLong(),
        )
    }
}
