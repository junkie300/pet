package io.github.junkie300.petapp.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdate
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.CompetitionUnit
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelManager
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.ui.common.ComingSoonScreen
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.OfflineBanner
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.place.PlaceListUiState
import io.github.junkie300.petapp.ui.place.PlaceListViewModel
import io.github.junkie300.petapp.ui.place.placeItems
import io.github.junkie300.petapp.ui.theme.CategoryColor
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.Spacing
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * S-02 장소 지도 — 고른 읍면동의 장소를 핀으로 찍는다 (spec.md §5.2).
 *
 * **목록과 같은 [PlaceListViewModel] 을 쓴다.** 지도와 목록은 같은 질문("이 동네의 이 카테고리")에
 * 대한 두 가지 그림일 뿐이라 조회를 두 벌 만들 이유가 없다 (D-26).
 *
 * 핀이 [MapClustering.THRESHOLD] 개를 넘으면 화면 격자로 묶어 개수를 적은 원으로 그린다 (D-78).
 */
@Composable
fun MapScreen(
    viewModel: PlaceListViewModel,
    onPlaceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 지도만 안내로 바뀐다. 앱은 그대로 돈다 (spec.md §1.2).
    // 키가 없는 것과 SDK 를 못 올린 것은 **다른 말이다** — 고칠 사람이 볼 곳이 서로 다르다.
    if (!KakaoMapProvider.isConfigured) {
        ComingSoonScreen(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.map_no_key_title),
            body = stringResource(R.string.map_no_key_body),
            modifier = modifier,
        )
        return
    }
    KakaoMapProvider.startupError?.let { error ->
        ComingSoonScreen(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.map_unsupported_title),
            body = stringResource(R.string.map_unsupported_body),
            note = error,
            modifier = modifier,
        )
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val ready = state as? PlaceListUiState.Ready

    val places = ((ready?.places as? UiState.Success)?.data).orEmpty()
    val pins = remember(places, selected) { places.toPins(selected.first()) }
    val region = ready?.region
    val regionCenter = remember(region) {
        val lat = region?.centerLat
        val lng = region?.centerLng
        if (lat != null && lng != null) LatLng.from(lat, lng) else null
    }

    // 지도가 검게 뜨는 사고는 예외도 로그도 분명하지 않다 (D-69). 받은 메시지를 화면에 그대로 적는다.
    var mapError by remember { mutableStateOf<String?>(null) }

    // 상단 알림이 지도를 가린다. 그만큼을 지도에 알려 줘야 핀이 알림 뒤로 들어가지 않는다 (D-73).
    var noticeHeightPx by remember { mutableIntStateOf(0) }

    // 지도에서 고른 핀. 시트에서 그 카드가 강조된다. **상세로 바로 가지 않는다** — 핀 하나를
    // 누른 것으로 화면을 통째로 바꾸면 지도를 훑어보던 맥락이 끊긴다 (D-74).
    var selectedPlaceId by rememberSaveable { mutableStateOf<Long?>(null) }

    val sheetState = rememberPlaceSheetState()
    val listState = rememberLazyListState()

    // 지역이 바뀌면 그전에 고른 핀은 이 목록에 없다. 그때만 지운다.
    // ⚠️ "지역 코드가 바뀌면 지운다"로 쓰면 안 된다 — 상세에 다녀오는 사이 지역이 잠시
    // null 이 되었다가 돌아오므로, 돌아올 때마다 선택이 사라진다(실측).
    LaunchedEffect(places) {
        if (places.isNotEmpty() && places.none { it.id == selectedPlaceId }) selectedPlaceId = null
    }

    LaunchedEffect(selectedPlaceId, places) {
        val index = places.indexOfFirst { it.id == selectedPlaceId }
        if (index < 0) return@LaunchedEffect
        // peek 은 목록이 거의 안 보인다. 핀을 눌렀으면 최소한 half 까지는 올려 준다.
        if (sheetState.settledValue == SheetDetent.PEEK) sheetState.animateTo(SheetDetent.HALF)
        listState.animateScrollToItem(index)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        // 시트가 아래를 덮는 만큼도 지도에 알려 준다. 안 그러면 아래쪽 핀이 시트 뒤에 숨는다.
        val sheetInsetPx = (containerHeightPx * SheetDetent.PEEK.visibleFraction).roundToInt()

        MapCanvas(
            center = regionCenter,
            pins = pins,
            topInsetPx = noticeHeightPx,
            bottomInsetPx = sheetInsetPx,
            onPinClick = { placeId -> selectedPlaceId = placeId },
            onMapError = { mapError = it.message ?: it.javaClass.simpleName },
            modifier = Modifier.fillMaxSize(),
        )

        // 상단에는 **지도에 관한 것**만 얹는다. 목록의 상태는 시트가 말한다.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = Spacing.screenHorizontal, vertical = 12.dp)
                .fillMaxWidth()
                .onSizeChanged { noticeHeightPx = it.height },
        ) {
            CategoryFilterChips(selected = selected, onToggle = viewModel::toggle)

            ready?.cachedAt?.let { OfflineBanner(cachedAt = it) }
            mapError?.let { error ->
                MapNotice(text = stringResource(R.string.map_error, error), emphasis = true)
            }
        }

        PlaceSheet(
            state = sheetState,
            containerHeightPx = containerHeightPx,
            listState = listState,
            header = { SheetHeader(title = sheetTitle(state, region, selected, places.size, pins.size)) },
        ) {
            when {
                state is PlaceListUiState.NoRegion -> Unit

                state is PlaceListUiState.Failed ->
                    item { FailedMessage(onRetry = viewModel::retry) }

                ready == null || ready.places is UiState.Loading ->
                    item { SkeletonRows(count = 4) }

                ready.places is UiState.Failed ->
                    item { FailedMessage(onRetry = viewModel::retry) }

                ready.places is UiState.Empty -> item {
                    // 칩이 하나면 그 이름으로, 여럿이면 뭉뚱그린다 — "이 지역에는 등록된
                    // 동물병원·미용 정보가" 처럼 이어 붙이면 조사가 어긋난다.
                    EmptyMessage(
                        text = selected.singleOrNull()
                            ?.let { stringResource(R.string.place_list_empty, stringResource(it.labelRes)) }
                            ?: stringResource(R.string.map_empty_multi),
                    )
                }

                else -> placeItems(
                    places = places,
                    onPlaceClick = { place -> onPlaceClick(place.id) },
                    selectedId = selectedPlaceId,
                )
            }
        }
    }
}

/**
 * 시트 머리말 문구.
 *
 * 칩을 하나만 켰으면 그 이름을 적고(`삼성동 · 동물병원 8곳`), 여럿이면 이름을 빼고 건수만 적는다 —
 * 칩 줄이 바로 위에 있으므로 무엇을 보고 있는지는 거기서 읽힌다.
 *
 * ⚠️ **좌표가 없어 핀이 못 된 장소는 반드시 밝힌다.** 조용히 빼면 시트의 건수와 지도의 핀 수가
 * 어긋나고, 사용자는 어느 쪽이 맞는지 알 수 없다.
 */
@Composable
private fun sheetTitle(
    state: PlaceListUiState,
    region: Region?,
    selected: Set<PlaceCategory>,
    placeCount: Int,
    pinCount: Int,
): String {
    if (state is PlaceListUiState.NoRegion) return stringResource(R.string.place_list_no_region)
    if (region == null) return stringResource(R.string.map_loading)

    val missing = placeCount - pinCount
    val single = selected.singleOrNull()?.let { stringResource(it.labelRes) }
    return when {
        single != null && missing > 0 ->
            stringResource(R.string.map_summary_missing_coords, region.fullName, single, pinCount, missing)

        single != null ->
            stringResource(R.string.map_summary, region.fullName, single, placeCount)

        missing > 0 ->
            stringResource(R.string.map_summary_multi_missing_coords, region.fullName, pinCount, missing)

        else -> stringResource(R.string.map_summary_multi, region.fullName, placeCount)
    }
}

/** 시트 머리말 — 지금 무엇을 보고 있는지 한 줄. 시트가 peek 이어도 이건 보인다. */
@Composable
private fun SheetHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 지도 위에 얹는 알림 한 칸. 지도가 배경이므로 반드시 불투명한 판 위에 올린다. */
@Composable
private fun MapNotice(text: String, emphasis: Boolean = false) {
    MapNotice {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasis) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun MapNotice(content: @Composable () -> Unit) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
    }
}

/** 지도에 찍을 한 점. 좌표가 없는 장소는 여기까지 오지 못한다. */
data class MapPin(
    val placeId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory,
)

private fun List<Place>.toPins(fallbackCategory: PlaceCategory): List<MapPin> = mapNotNull { place ->
    val lat = place.lat ?: return@mapNotNull null
    val lng = place.lng ?: return@mapNotNull null
    MapPin(place.id, place.name, lat, lng, place.placeCategory ?: fallbackCategory)
}

/**
 * 카카오맵 [MapView] 를 Compose 안에 얹는다.
 *
 * [MapView] 는 안드로이드 뷰이고 자기 수명주기를 따로 갖는다 — resume·pause·finish 를 짝 맞춰
 * 불러 주지 않으면 탭을 옮길 때마다 렌더러가 남는다.
 */
@Composable
private fun MapCanvas(
    center: LatLng?,
    pins: List<MapPin>,
    /** 상단 알림에 가려지는 높이(px). 카메라가 이만큼을 빼고 화면을 잡는다. */
    topInsetPx: Int,
    /** 바텀시트에 가려지는 높이(px). 같은 이유로 아래쪽도 빼 준다. */
    bottomInsetPx: Int,
    onPinClick: (Long) -> Unit,
    onMapError: (Exception) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val fitPaddingPx = remember(density) { with(density) { FIT_PADDING.roundToPx() } }

    // 콜백은 지도를 시작할 때 한 번만 등록된다. 그 안에서 최신 람다·값을 보게 해 둔다.
    val currentPinClick by rememberUpdatedState(onPinClick)
    val currentMapError by rememberUpdatedState(onMapError)
    val currentCenter by rememberUpdatedState(center)
    val currentFitPadding by rememberUpdatedState(fitPaddingPx)

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    // 지금 배율. 묶는 기준이 화면 픽셀이라 배율이 바뀌면 다시 묶어야 한다.
    // ⚠️ 카메라가 움직일 때마다 다시 그리는 것이 아니다 — 배율이 그대로면 remember 가 이전
    // 묶음을 그대로 돌려주므로, 지도를 밀기만 할 때는 핀을 건드리지 않는다.
    var zoomLevel by remember { mutableIntStateOf(REGION_ZOOM) }

    val cellPx = remember(density) { with(density) { MapClustering.CELL_DP.dp.roundToPx() } }
    val clusters = remember(pins, zoomLevel, cellPx) { MapClustering.cluster(pins, zoomLevel, cellPx) }

    val mapView = remember {
        MapView(context).apply {
            start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit

                    // 키가 틀리거나 콘솔에 패키지명·키 해시가 없으면 여기로 온다 (D-69).
                    override fun onMapError(error: Exception) = currentMapError(error)
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        map.setOnLabelClickListener { clicked, _, label ->
                            when (val tag = label.tag) {
                                // 핀 하나 — 시트에서 그 카드를 강조한다 (D-74).
                                is Long -> {
                                    currentPinClick(tag)
                                    true
                                }

                                // 묶음 — 펼친다. 시트로도 상세로도 가지 않는다. 사용자가 물은 것은
                                // "여기 뭐가 있나"이지 "그중 하나를 보여 달라"가 아니다.
                                is MapCluster -> {
                                    clicked.moveCamera(tag.expandCamera(clicked, currentFitPadding))
                                    true
                                }

                                else -> false
                            }
                        }
                        map.setOnCameraMoveEndListener { _, position, _ -> zoomLevel = position.zoomLevel }
                        zoomLevel = map.zoomLevel
                        kakaoMap = map
                    }

                    // 지도가 준비되기 전의 첫 화면 위치. 고른 지역이 없으면 전국이 보이는 자리다.
                    override fun getPosition(): LatLng = currentCenter ?: DEFAULT_CENTER

                    override fun getZoomLevel(): Int =
                        if (currentCenter == null) NATIONWIDE_ZOOM else REGION_ZOOM
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    // 지도에게 "이만큼은 가려져 있다"고 알려 준다. 이걸 안 하면 fitMapPoints 가 잡아 준 위쪽
    // 핀이 상단 알림 뒤로 들어간다 — 화면으로 보기 전에는 드러나지 않는 종류의 어긋남이다.
    LaunchedEffect(kakaoMap, topInsetPx, bottomInsetPx) {
        kakaoMap?.setPadding(0, topInsetPx, 0, bottomInsetPx)
    }

    // 카메라는 **목록이 바뀔 때만** 잡는다. 여기에 배율을 끼워 넣으면 다시 묶기 → 카메라 이동 →
    // 배율 변화 → 다시 묶기로 되돌아 도는 고리가 된다.
    LaunchedEffect(kakaoMap, pins) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (pins.isEmpty()) {
            center?.let { map.moveCamera(CameraUpdateFactory.newCenterPosition(it, REGION_ZOOM)) }
            return@LaunchedEffect
        }
        // 한 점만 있으면 fitMapPoints 가 최대 배율까지 당겨 버린다. 그때는 그 점을 중심으로만 잡는다.
        val points = pins.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray()
        map.moveCamera(
            if (points.size == 1) {
                CameraUpdateFactory.newCenterPosition(points.first(), REGION_ZOOM)
            } else {
                CameraUpdateFactory.fitMapPoints(points, fitPaddingPx)
            },
        )
    }

    // 핀은 지도가 준비된 뒤에만 그릴 수 있다. 묶음이 바뀌면 통째로 다시 그린다 — 한 읍면동은
    // 수십~수백 개라, 지우고 다시 찍는 편이 차이를 계산하는 것보다 싸고 안전하다.
    LaunchedEffect(kakaoMap, clusters) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labels = map.labelManager ?: return@LaunchedEffect
        val pinLayer = labels.layerFor(PIN_LAYER_ID, CompetitionType.None, clickable = true)
            ?: return@LaunchedEffect
        val nameLayer = labels.layerFor(NAME_LAYER_ID, CompetitionType.All, clickable = false)
            ?: return@LaunchedEffect
        pinLayer.removeAll()
        nameLayer.removeAll()
        if (clusters.isEmpty()) return@LaunchedEffect

        // 스타일은 그림 한 장마다 하나면 된다. 핀 개수만큼 만들면 같은 그림을 수십 벌 올리게 된다.
        val pinStyles = mutableMapOf<PlaceCategory, LabelStyles?>()
        val clusterStyles = mutableMapOf<Pair<PlaceCategory, Int>, LabelStyles?>()
        val nameStyles = labels.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(spacerBitmap(context))
                    .setTextStyles(PIN_TEXT_SIZE, PIN_TEXT_COLOR, PIN_TEXT_STROKE, PIN_TEXT_STROKE_COLOR),
            ),
        )

        val pinOptions = mutableListOf<LabelOptions>()
        val nameOptions = mutableListOf<LabelOptions>()
        for (cluster in clusters) {
            val position = LatLng.from(cluster.latitude, cluster.longitude)
            val single = cluster.single
            if (single == null) {
                val category = cluster.dominantCategory
                val styles = clusterStyles.getOrPut(category to cluster.size) {
                    labels.addLabelStyles(
                        LabelStyles.from(
                            LabelStyle.from(clusterBitmap(context, category, cluster.size)),
                        ),
                    )
                }
                pinOptions += LabelOptions.from(position)
                    .setStyles(styles)
                    .setClickable(true)
                    // 눌렀을 때 무엇을 펼칠지 되찾는 끈이다.
                    .setTag(cluster)
            } else {
                val styles = pinStyles.getOrPut(single.category) {
                    labels.addLabelStyles(LabelStyles.from(LabelStyle.from(pinBitmap(context, single.category))))
                }
                pinOptions += LabelOptions.from(position)
                    .setStyles(styles)
                    .setClickable(true)
                    // 핀을 눌렀을 때 어느 장소인지 되찾는 유일한 끈이다.
                    .setTag(single.placeId)
                nameOptions += LabelOptions.from(position)
                    .setStyles(nameStyles)
                    .setTexts(LabelTextBuilder().setTexts(single.name))
                    .setClickable(false)
            }
        }

        pinLayer.addLabels(pinOptions)
        if (nameOptions.isNotEmpty()) nameLayer.addLabels(nameOptions)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * 핀 그림. 한 장을 카테고리 색으로 tint 해서 쓴다 — 색깔별 파일을 두지 않는다.
 *
 * ⚠️ 지도 바탕은 테마와 무관하게 밝다. 그래서 다크 짝(`CategoryColor.*Dark`)이 아니라
 * **라이트 기준 5색**을 쓴다. 어두운 카드 위에서 밝은 짝을 쓰는 것과 같은 이유다 (D-56).
 */
private fun pinBitmap(context: Context, category: PlaceCategory): Bitmap {
    val drawable = requireNotNull(context.getDrawable(R.drawable.ic_map_pin)).mutate()
    DrawableCompat.setTint(drawable, pinColor(category))
    return drawable.toBitmap()
}

private fun pinColor(category: PlaceCategory) = when (category) {
    PlaceCategory.HOSPITAL -> CategoryColor.Vet
    PlaceCategory.GROOMING -> CategoryColor.Grooming
    PlaceCategory.RESTAURANT -> CategoryColor.Restaurant
    PlaceCategory.TOUR -> CategoryColor.Tour
    PlaceCategory.WILDLIFE_CENTER -> CategoryColor.WildlifeCenter
}.toArgb()

/**
 * 핀을 얹을 레이어. **그림과 이름을 다른 레이어에 나눠 놓는다** (D-78).
 *
 * 한 레이어에 그림과 이름을 같이 두고 겹침 경쟁을 켜면, 진 쪽은 **이름만이 아니라 핀째로**
 * 사라진다. 지도에서 장소가 통째로 없어지는 것과 이름 하나가 생략되는 것은 전혀 다른 일이다.
 * - 그림 레이어: 경쟁 없음. 좌표가 있는 장소는 **반드시 점 하나로 보인다**
 * - 이름 레이어: 경쟁 켬. 자리가 없으면 이름만 빠진다 (D-73 의 "이름이 서로 겹친다")
 */
private fun LabelManager.layerFor(
    layerId: String,
    competitionType: CompetitionType,
    clickable: Boolean,
): LabelLayer? =
    // 이미 만들어 둔 레이어가 있으면 그것을 쓴다. 같은 id 로 다시 만들면 SDK 가 거절한다.
    runCatching { getLayer(layerId) }.getOrNull()
        ?: addLayer(
            LabelLayerOptions.from(layerId)
                .setCompetitionType(competitionType)
                .setCompetitionUnit(CompetitionUnit.IconAndText)
                .setClickable(clickable),
        )

/**
 * 묶음을 눌렀을 때의 카메라. **확대 배율을 정해 놓지 않는다** — 그 묶음이 실제로 갈라지는
 * 자리까지 맞춰야 한 번 눌러 안 갈라지는 일이 없다.
 */
private fun MapCluster.expandCamera(map: KakaoMap, paddingPx: Int): CameraUpdate =
    if (isSinglePoint()) {
        // 같은 건물에 여럿 있으면 아무리 당겨도 갈라지지 않는다. 그 자리로 한 단계만 다가서고
        // 나머지는 시트의 목록이 말한다 — 눌러도 아무 일이 없는 것보다 낫다.
        CameraUpdateFactory.newCenterPosition(
            LatLng.from(latitude, longitude),
            min(map.zoomLevel + EXPAND_STEP, map.maxZoomLevel),
        )
    } else {
        CameraUpdateFactory.fitMapPoints(
            pins.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray(),
            paddingPx,
        )
    }

/**
 * 묶음 그림 — 카테고리 색 원 + 개수. **개수를 그림 안에 그려 넣는다.**
 * 라벨 글자는 그림 아래에 붙으므로, 개수를 글자로 얹으면 원 밑에 숫자가 떨어져 붙는다.
 */
private fun clusterBitmap(context: Context, category: PlaceCategory, count: Int): Bitmap {
    val scale = context.resources.displayMetrics.density
    val diameterDp = when {
        count < 10 -> CLUSTER_SMALL_DP
        count < 100 -> CLUSTER_MEDIUM_DP
        else -> CLUSTER_LARGE_DP
    }
    val size = (diameterDp * scale).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val ringWidth = CLUSTER_RING_DP * scale

    canvas.drawCircle(center, center, center - ringWidth, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor(category)
    })
    canvas.drawCircle(center, center, center - ringWidth / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        color = CLUSTER_RING_COLOR
    })

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLUSTER_TEXT_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = CLUSTER_TEXT_DP * scale
    }
    // baseline 은 글자의 아래쪽이다. 그대로 가운데에 두면 숫자가 아래로 치우쳐 보인다.
    canvas.drawText(count.toString(), center, center - (text.descent() + text.ascent()) / 2f, text)
    return bitmap
}

/**
 * 이름 레이어가 쓰는 **투명한 핀 자리**. 그림과 같은 크기여야 이름이 지금까지와 같은 자리에 붙는다 —
 * 없이 두면 이름이 핀 그림 위에 겹쳐 앉는다.
 */
private fun spacerBitmap(context: Context): Bitmap {
    val drawable = requireNotNull(context.getDrawable(R.drawable.ic_map_pin))
    return Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
}

/** 고른 지역이 없을 때의 첫 화면 — 남북으로 전국이 고르게 들어오는 자리다. */
private val DEFAULT_CENTER = LatLng.from(36.3, 127.8)
private const val NATIONWIDE_ZOOM = 7
private const val REGION_ZOOM = 14

/**
 * fitMapPoints 의 여백. 핀 옆에 이름이 붙으므로 그림 크기보다 넉넉히 준다.
 * ⚠️ 더 키우면 여백을 확보하려고 **축척이 통째로 물러난다** — 이름끼리 겹치는 것은
 * 여백이 아니라 클러스터링(④)으로 푼다.
 */
private val FIT_PADDING = 40.dp

private const val PIN_LAYER_ID = "places"
private const val NAME_LAYER_ID = "place-names"

/** 묶음을 눌렀는데 갈라지지 않을 때 다가서는 단계. 한 번에 너무 당기면 어디였는지 놓친다. */
private const val EXPAND_STEP = 2

private const val CLUSTER_SMALL_DP = 36f
private const val CLUSTER_MEDIUM_DP = 44f
private const val CLUSTER_LARGE_DP = 52f
private const val CLUSTER_RING_DP = 2.5f
private const val CLUSTER_RING_COLOR = 0xFFFFFFFF.toInt()
private const val CLUSTER_TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val CLUSTER_TEXT_DP = 15f

private const val PIN_TEXT_SIZE = 26
private const val PIN_TEXT_COLOR = 0xFF1A1A1A.toInt()
private const val PIN_TEXT_STROKE = 3
private const val PIN_TEXT_STROKE_COLOR = 0xFFFFFFFF.toInt()
