package com.whatsappworkmanager.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.presentation.dashboard.DashboardScreen
import com.whatsappworkmanager.app.presentation.keywords.KeywordRulesScreen
import com.whatsappworkmanager.app.presentation.onboarding.OnboardingScreen
import com.whatsappworkmanager.app.presentation.people.ImportantPeopleScreen
import com.whatsappworkmanager.app.presentation.privacy.PrivacyScreen
import com.whatsappworkmanager.app.presentation.replyphrases.ReplyPhrasesScreen
import com.whatsappworkmanager.app.presentation.scheduled.ScheduledMessagesScreen
import com.whatsappworkmanager.app.presentation.search.MessageFilter
import com.whatsappworkmanager.app.presentation.search.SearchScreen
import com.whatsappworkmanager.app.presentation.settings.SettingsScreen
import com.whatsappworkmanager.app.presentation.summary.SummaryScreen
import com.whatsappworkmanager.app.presentation.scheduling.WorkSchedulesScreen
import com.whatsappworkmanager.app.presentation.workgroups.WorkGroupsScreen

@Composable
fun WwmNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val onboardingDone by app.settingsDataStore.onboardingDone.collectAsState(initial = null)

    // Wait for the first DataStore read before deciding the start destination, so we never
    // briefly flash onboarding for a returning user.
    val startDestination = when (onboardingDone) {
        null -> return // still loading; render nothing for a frame rather than guessing
        true -> Screen.Dashboard.route
        false -> Screen.Onboarding.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onFinished = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onOpenSummary = { navController.navigate(Screen.Summary.route) },
                onOpenWorkGroups = { navController.navigate(Screen.WorkGroups.route) },
                onOpenSearchFiltered = { filter -> navController.navigate(Screen.Search.routeFor(filter)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenKeywordRules = { navController.navigate(Screen.KeywordRules.route) },
                onOpenImportantPeople = { navController.navigate(Screen.ImportantPeople.route) },
                onOpenReplyPhrases = { navController.navigate(Screen.ReplyPhrases.route) },
                onOpenQuickChat = { navController.navigate(Screen.QuickChat.route) },
                onOpenAutoReply = { navController.navigate(Screen.AutoReply.route) },
                onOpenScheduledMessages = { navController.navigate(Screen.ScheduledMessages.route) }
            )
        }
        composable(Screen.WorkGroups.route) {
            WorkGroupsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.QuickChat.route) {
            com.whatsappworkmanager.app.presentation.quickchat.QuickChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Summary.route) { SummaryScreen() }
        composable(
            route = Screen.Search.route,
            arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "ALL" })
        ) { backStackEntry ->
            val filterArg = backStackEntry.arguments?.getString("filter") ?: "ALL"
            val initialFilter = runCatching { MessageFilter.valueOf(filterArg) }.getOrDefault(MessageFilter.ALL)
            SearchScreen(initialFilter = initialFilter)
        }
        composable(Screen.ScheduledMessages.route) { ScheduledMessagesScreen() }
        composable(Screen.WorkSchedules.route) { WorkSchedulesScreen() }
        composable(Screen.ImportantPeople.route) { ImportantPeopleScreen() }
        composable(Screen.KeywordRules.route) { KeywordRulesScreen() }
        composable(Screen.ReplyPhrases.route) { ReplyPhrasesScreen() }
        composable(Screen.AutoReply.route) { com.whatsappworkmanager.app.presentation.autoreply.AutoReplyScreen() }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                onOpenScheduledMessages = { navController.navigate(Screen.ScheduledMessages.route) },
                onOpenWorkSchedules = { navController.navigate(Screen.WorkSchedules.route) },
                onOpenImportantPeople = { navController.navigate(Screen.ImportantPeople.route) },
                onOpenKeywordRules = { navController.navigate(Screen.KeywordRules.route) },
                onOpenReplyPhrases = { navController.navigate(Screen.ReplyPhrases.route) }
            )
        }
        composable(Screen.Privacy.route) { PrivacyScreen() }
    }
}
