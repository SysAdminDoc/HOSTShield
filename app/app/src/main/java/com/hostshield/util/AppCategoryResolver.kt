package com.hostshield.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * v5.1: Resolves Android app categories for category-based blocking.
 *
 * Uses ApplicationInfo.category (API 26+) which maps to Play Store categories.
 * Falls back to heuristic-based classification for apps without category metadata.
 *
 * Categories are used by the Firewall screen for bulk operations:
 * "Block all Social apps", "Block all Games when screen off", etc.
 *
 * Inspired by RethinkDNS's category-based blocking which groups apps by
 * Play Store categories for non-technical users.
 */
object AppCategoryResolver {

    private const val TAG = "AppCategoryResolver"

    /**
     * Simplified app category labels for UI display.
     * Maps Android's ApplicationInfo.CATEGORY_* constants to user-friendly names.
     */
    enum class AppCategory(val label: String, val icon: String) {
        GAME("Games", "sports_esports"),
        AUDIO("Audio & Music", "music_note"),
        VIDEO("Video & Media", "videocam"),
        IMAGE("Photo & Image", "photo_camera"),
        SOCIAL("Social", "people"),
        NEWS("News & Magazines", "newspaper"),
        MAPS("Maps & Navigation", "map"),
        PRODUCTIVITY("Productivity", "work"),
        ACCESSIBILITY("Accessibility", "accessibility"),
        SYSTEM("System", "settings"),
        OTHER("Other", "apps");

        companion object {
            /**
             * Map Android category int to our enum.
             * ApplicationInfo.CATEGORY_* constants (API 26+).
             */
            fun fromAndroidCategory(category: Int): AppCategory = when (category) {
                ApplicationInfo.CATEGORY_GAME -> GAME
                ApplicationInfo.CATEGORY_AUDIO -> AUDIO
                ApplicationInfo.CATEGORY_VIDEO -> VIDEO
                ApplicationInfo.CATEGORY_IMAGE -> IMAGE
                ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> NEWS
                ApplicationInfo.CATEGORY_MAPS -> MAPS
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> ACCESSIBILITY
                else -> OTHER
            }
        }
    }

    data class CategorizedApp(
        val packageName: String,
        val appLabel: String,
        val category: AppCategory,
        val uid: Int,
        val isSystem: Boolean
    )

    /**
     * Categorize all installed apps.
     *
     * @param context Application context
     * @param includeSystem Whether to include system apps
     * @return Map of category to list of apps in that category
     */
    fun categorizeApps(context: Context, includeSystem: Boolean = true): Map<AppCategory, List<CategorizedApp>> {
        val pm = context.packageManager
        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps: ${e.message}")
            return emptyMap()
        }

        val categorized = apps.mapNotNull { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && isSystem) return@mapNotNull null

            val category = resolveCategory(appInfo, pm)
            CategorizedApp(
                packageName = appInfo.packageName,
                appLabel = appInfo.loadLabel(pm).toString(),
                category = category,
                uid = appInfo.uid,
                isSystem = isSystem
            )
        }

        return categorized
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
    }

    /**
     * Get category for a single package.
     */
    fun getCategory(context: Context, packageName: String): AppCategory {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            resolveCategory(appInfo, context.packageManager)
        } catch (_: Exception) {
            AppCategory.OTHER
        }
    }

    /**
     * Get all packages in a specific category.
     */
    fun getPackagesInCategory(context: Context, category: AppCategory): List<String> {
        return categorizeApps(context)[category]?.map { it.packageName } ?: emptyList()
    }

    private fun resolveCategory(appInfo: ApplicationInfo, pm: PackageManager): AppCategory {
        // API 26+: Use the built-in category
        val androidCat = appInfo.category
        if (androidCat != ApplicationInfo.CATEGORY_UNDEFINED) {
            return AppCategory.fromAndroidCategory(androidCat)
        }

        // Heuristic fallback for apps without category metadata
        val pkg = appInfo.packageName
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        return when {
            isSystem -> AppCategory.SYSTEM
            // Common social app packages
            pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("twitter") ||
                pkg.contains("tiktok") || pkg.contains("snapchat") || pkg.contains("whatsapp") ||
                pkg.contains("telegram") || pkg.contains("signal") || pkg.contains("discord") ||
                pkg.contains("reddit") || pkg.contains("linkedin") || pkg.contains("pinterest") ||
                pkg.contains("threads") || pkg.contains("mastodon") -> AppCategory.SOCIAL
            // Common game packages
            pkg.contains("game") || pkg.contains("unity") -> AppCategory.GAME
            // Common media packages
            pkg.contains("spotify") || pkg.contains("music") || pkg.contains("podcast") ||
                pkg.contains("soundcloud") || pkg.contains("deezer") -> AppCategory.AUDIO
            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("twitch") ||
                pkg.contains("plex") || pkg.contains("vlc") -> AppCategory.VIDEO
            pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photo") -> AppCategory.IMAGE
            pkg.contains("maps") || pkg.contains("waze") || pkg.contains("navigation") -> AppCategory.MAPS
            pkg.contains("news") || pkg.contains("nytimes") || pkg.contains("bbc") -> AppCategory.NEWS
            else -> AppCategory.OTHER
        }
    }
}
