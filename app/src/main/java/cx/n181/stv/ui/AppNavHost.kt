package cx.n181.stv.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onOpenSearch = { navController.navigate("search") },
                onOpenSettings = { navController.navigate("settings") },
                onSearchTitle = { title ->
                    navController.navigate("search?query=${Uri.encode(title)}")
                },
                onOpenDetail = { sourceKey, videoId ->
                    navController.navigate("detail/$sourceKey/$videoId")
                }
            )
        }

        composable("search") {
            SearchScreen(
                onOpenDetail = { sourceKey, videoId ->
                    navController.navigate("detail/$sourceKey/$videoId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "search?query={query}",
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            SearchScreen(
                initialQuery = entry.arguments?.getString("query").orEmpty(),
                onOpenDetail = { sourceKey, videoId ->
                    navController.navigate("detail/$sourceKey/$videoId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "detail/{sourceKey}/{videoId}",
            arguments = listOf(
                navArgument("sourceKey") { type = NavType.StringType },
                navArgument("videoId") { type = NavType.StringType }
            )
        ) { entry ->
            val sourceKey = entry.arguments?.getString("sourceKey").orEmpty()
            val videoId = entry.arguments?.getString("videoId").orEmpty()

            DetailScreen(
                sourceKey = sourceKey,
                videoId = videoId,
                onPlay = { episodeIndex, startPositionMs ->
                    navController.navigate(
                        "player/$sourceKey/$videoId/$episodeIndex/$startPositionMs"
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "player/{sourceKey}/{videoId}/{episodeIndex}/{startPositionMs}",
            arguments = listOf(
                navArgument("sourceKey") { type = NavType.StringType },
                navArgument("videoId") { type = NavType.StringType },
                navArgument("episodeIndex") { type = NavType.IntType },
                navArgument("startPositionMs") { type = NavType.LongType }
            )
        ) { entry ->
            val sourceKey = entry.arguments?.getString("sourceKey").orEmpty()
            val videoId = entry.arguments?.getString("videoId").orEmpty()
            val episodeIndex = entry.arguments?.getInt("episodeIndex") ?: 0
            val startPositionMs = entry.arguments?.getLong("startPositionMs") ?: 0L

            PlayerScreen(
                sourceKey = sourceKey,
                videoId = videoId,
                episodeIndex = episodeIndex,
                startPositionMs = startPositionMs,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
