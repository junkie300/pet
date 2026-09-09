package io.github.junkie300.petapp.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.junkie300.petapp.AppContainer
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.ComingSoonScreen
import io.github.junkie300.petapp.ui.favorite.FavoritesScreen
import io.github.junkie300.petapp.ui.favorite.FavoritesViewModel
import io.github.junkie300.petapp.ui.home.HomeScreen
import io.github.junkie300.petapp.ui.home.HomeViewModel
import io.github.junkie300.petapp.ui.map.KakaoMapProvider
import io.github.junkie300.petapp.ui.map.MapScreen
import io.github.junkie300.petapp.ui.more.MoreScreen
import io.github.junkie300.petapp.ui.place.PlaceDetailScreen
import io.github.junkie300.petapp.ui.place.PlaceDetailViewModel
import io.github.junkie300.petapp.ui.place.PlaceListScreen
import io.github.junkie300.petapp.ui.place.PlaceListViewModel
import io.github.junkie300.petapp.ui.region.RegionPickerScreen
import io.github.junkie300.petapp.ui.region.RegionPickerViewModel

/**
 * 하단 탭 4개 (spec.md §5.1 · D-39).
 *
 * 시안은 마지막 탭이 "마이"였는데, 로그인 없는 앱이라 계정 화면이 들어갈 자리가 없어
 * **더보기**로 이름만 바꿔 받았다.
 */
enum class PetTab(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    MAP("map", R.string.tab_map, Icons.Outlined.Map),
    ADOPTION("adoption", R.string.tab_adoption, Icons.Outlined.VolunteerActivism),
    MORE("more", R.string.tab_more, Icons.Outlined.MoreHoriz),
}

/** S-01 은 탭이 아니다. 홈의 지역 칩에서 밀어 올리는 화면이다 (D-39). */
private const val ROUTE_REGION = "region"

/** 홈 그리드에서 카테고리를 눌러 들어올 때 실어 보내는 값. 없이 들어오면 전체다. */
private const val ARG_CATEGORY = "category"
private val MAP_ROUTE_PATTERN = "${PetTab.MAP.route}?$ARG_CATEGORY={$ARG_CATEGORY}"

/**
 * 장소 목록·상세. **탭이 아니다** — 홈 위에 얹는다.
 * 지도가 붙으면 목록은 S-02 의 바텀시트로 들어가지만, 상세는 이 자리에 그대로 남는다.
 */
private const val ARG_PLACE_ID = "placeId"
private val PLACES_ROUTE_PATTERN = "places/{$ARG_CATEGORY}"
private val PLACE_DETAIL_ROUTE_PATTERN = "place/{$ARG_PLACE_ID}"

/** S-07 즐겨찾기. 더보기에서 여는 화면이며 탭이 아니다. */
private const val ROUTE_FAVORITES = "favorites"

@Composable
fun PetApp(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // 라우트에 인자가 붙으면 "map?category={category}" 가 되므로 '?' 앞만 비교한다.
    val currentTab = PetTab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.route?.substringBefore('?') == tab.route } == true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 지역 선택처럼 탭 위에 얹히는 화면에서는 탭바를 숨긴다.
            if (currentTab != null) {
                PetBottomBar(current = currentTab, navController = navController)
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = PetTab.HOME.route,
            modifier = Modifier.padding(insets),
        ) {
            composable(PetTab.HOME.route) {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        container.regionRepository,
                        container.placeRepository,
                        container.recentRegionStore,
                    ),
                )
                HomeScreen(
                    viewModel = viewModel,
                    onRegionClick = { navController.navigate(ROUTE_REGION) },
                    // 카테고리 타일 → **그 카테고리로 필터된 지도** (spec.md §5.1).
                    // ⚠️ 지도는 탭이다. navigate() 로 홈 위에 얹으면 홈 탭의 백스택에 딸려
                    // 들어가 다음에 홈을 눌렀을 때 지도가 되살아난다 (D-55).
                    onCategoryClick = { category ->
                        if (KakaoMapProvider.isUsable) {
                            navController.switchTab("${PetTab.MAP.route}?$ARG_CATEGORY=${category.dbValue}")
                        } else {
                            // ⚠️ 지도를 못 여는 기기에서 지도로 보내면 **막다른 안내가 전부다** (D-71).
                            // 그때는 목록 화면이 그 자리를 대신한다 — 조회도 카드도 같은 것을 쓴다 (D-80).
                            navController.navigate("places/${category.dbValue}")
                        }
                    },
                )
            }

            composable(ROUTE_REGION) {
                val viewModel: RegionPickerViewModel = viewModel(
                    factory = RegionPickerViewModel.factory(
                        container.regionRepository,
                        container.recentRegionStore,
                    ),
                )
                RegionPickerScreen(
                    viewModel = viewModel,
                    // 지역을 정하면 홈으로 돌아간다. 홈은 최근 지역 저장소를 보고 있으므로
                    // 따로 결과를 넘기지 않아도 새 지역으로 다시 그린다.
                    onRegionConfirmed = { navController.popBackStack() },
                )
            }

            composable(
                route = PLACES_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_CATEGORY) { type = NavType.StringType }),
            ) { entry ->
                // 모르는 카테고리로 들어오면 홈으로 돌려보낸다. 빈 화면을 내놓지 않는다.
                val category = PlaceCategory.fromDbValue(entry.arguments?.getString(ARG_CATEGORY))
                if (category == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val viewModel: PlaceListViewModel = viewModel(
                        key = category.dbValue,
                        factory = PlaceListViewModel.factory(
                            setOf(category),
                            container.regionRepository,
                            container.placeRepository,
                            container.deviceLocation,
                            container.recentRegionStore,
                        ),
                    )
                    PlaceListScreen(
                        viewModel = viewModel,
                        category = category,
                        onBack = { navController.popBackStack() },
                        onPlaceClick = { place -> navController.navigate("place/${place.id}") },
                    )
                }
            }

            composable(
                route = PLACE_DETAIL_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_PLACE_ID) { type = NavType.LongType }),
            ) { entry ->
                val placeId = entry.arguments?.getLong(ARG_PLACE_ID) ?: 0L
                val viewModel: PlaceDetailViewModel = viewModel(
                    factory = PlaceDetailViewModel.factory(
                        placeId,
                        container.placeRepository,
                        container.favoriteDao,
                    ),
                )
                PlaceDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = MAP_ROUTE_PATTERN,
                arguments = listOf(
                    navArgument(ARG_CATEGORY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                // 홈 타일에서 카테고리를 실어 보냈으면 그것만, 그냥 탭으로 들어왔으면 적재된 전부.
                val fromHome = PlaceCategory.fromDbValue(entry.arguments?.getString(ARG_CATEGORY))
                val loaded = PlaceCategory.loadedEntries.toSet().ifEmpty { setOf(PlaceCategory.HOSPITAL) }
                // ⚠️ ViewModel 은 **하나뿐이다.** 카테고리별로 key 를 나누면 칩으로 켠 선택이
                // 카테고리마다 따로 살아 있어, 칩을 끄고 켤 때마다 다른 목록이 되살아난다 (D-76).
                val viewModel: PlaceListViewModel = viewModel(
                    factory = PlaceListViewModel.factory(
                        fromHome?.let { setOf(it) } ?: loaded,
                        container.regionRepository,
                        container.placeRepository,
                        container.deviceLocation,
                        container.recentRegionStore,
                    ),
                )
                // 이미 열려 있는 지도로 홈 타일이 다시 들어오면 그 카테고리로 바꿔 준다.
                LaunchedEffect(fromHome) { fromHome?.let { viewModel.select(setOf(it)) } }
                MapScreen(
                    viewModel = viewModel,
                    onPlaceClick = { placeId -> navController.navigate("place/$placeId") },
                    // 지도를 못 여는 기기의 빠져나갈 길 (D-89). 홈 타일과 **같은 곳**으로 보낸다
                    // (D-80). 목록은 카테고리 하나짜리이므로, 타일로 들어왔으면 그 카테고리로
                    // 그냥 탭으로 들어왔으면 적재된 것 중 첫째로 연다.
                    onShowList = {
                        val category = fromHome
                            ?: PlaceCategory.loadedEntries.firstOrNull()
                            ?: PlaceCategory.HOSPITAL
                        navController.navigate("places/${category.dbValue}")
                    },
                )
            }

            composable(PetTab.ADOPTION.route) {
                ComingSoonScreen(
                    icon = PetTab.ADOPTION.icon,
                    title = stringResource(R.string.coming_soon_adoption_title),
                    body = stringResource(R.string.coming_soon_adoption_body),
                )
            }

            composable(PetTab.MORE.route) {
                MoreScreen(onFavoritesClick = { navController.navigate(ROUTE_FAVORITES) })
            }

            composable(ROUTE_FAVORITES) {
                val viewModel: FavoritesViewModel = viewModel(
                    factory = FavoritesViewModel.factory(container.favoriteDao, container.deviceLocation),
                )
                FavoritesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onPlaceClick = { placeId -> navController.navigate("place/$placeId") },
                )
            }
        }
    }
}

/**
 * 탭 사이 이동. 하단 탭과 홈 그리드가 **같은 방식**으로 움직여야 한다.
 *
 * 하나만 평범한 navigate() 로 밀어 넣으면 그 화면이 홈 탭의 백스택에 얹혀서,
 * 뒤에 홈 탭을 눌렀을 때 홈 대신 그 화면이 되살아난다.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        // 탭을 오갈 때 백스택이 무한히 쌓이지 않게 한다.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PetBottomBar(current: PetTab, navController: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        PetTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = {
                    if (tab == current) return@NavigationBarItem
                    navController.switchTab(tab.route)
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                // 라벨을 항상 보여준다 — 아이콘만으로는 "구조·입양"과 "더보기"가 구분되지 않는다.
                label = { Text(stringResource(tab.labelRes)) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
