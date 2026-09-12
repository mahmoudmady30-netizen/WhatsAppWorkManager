package com.whatsappworkmanager.app.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object WorkGroups : Screen("work_groups")
    object Summary : Screen("summary")
    // "filter" selects which MessageFilter (see SearchViewModel) the Search screen opens with —
    // e.g. tapping the "Important" stat card on the Dashboard opens Search pre-filtered to
    // important messages, instead of always landing on the unfiltered "All" list.
    object Search : Screen("search?filter={filter}") {
        fun routeFor(filter: String) = "search?filter=$filter"
    }
    object ScheduledMessages : Screen("scheduled_messages")
    object WorkSchedules : Screen("work_schedules")
    object ImportantPeople : Screen("important_people")
    object KeywordRules : Screen("keyword_rules")
    object ReplyPhrases : Screen("reply_phrases")
    object AutoReply : Screen("auto_reply")
    object QuickChat : Screen("quick_chat")
    object Settings : Screen("settings")
    object Privacy : Screen("privacy")
}
