package com.dietox.donate.util

import android.content.Context
import android.content.Intent

/** 사용자가 고를 수 있는 설치된 앱 하나. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    /** 치지직으로 보이는 앱인지. 목록 맨 위로 올려 준다. */
    val isCandidate: Boolean,
)

object InstalledApps {

    /** 치지직으로 알려진 패키지 이름 후보들. 바뀔 수 있어 목록에서 직접 고르게 한다. */
    private val CHZZK_CANDIDATES = listOf("chzzk", "naver.chzzk")

    private val CHZZK_LABELS = listOf("치지직", "chzzk")

    fun load(context: Context): List<InstalledApp> {
        val manager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return manager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                val label = runCatching { manager.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName)
                InstalledApp(
                    packageName = info.packageName,
                    label = label,
                    isCandidate = looksLikeChzzk(info.packageName, label),
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.isCandidate }.thenBy { it.label })
            .toList()
    }

    fun labelFor(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun looksLikeChzzk(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        return CHZZK_CANDIDATES.any { pkg.contains(it) } || CHZZK_LABELS.any { name.contains(it) }
    }
}
