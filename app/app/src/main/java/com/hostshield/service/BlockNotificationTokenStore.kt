package com.hostshield.service

import com.hostshield.data.preferences.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short-lived, one-use notification capabilities persisted in HostShield's
 * Keystore-backed store so actions survive process recreation. A custom URI
 * without a matching capability only opens plain logs and cannot authorize an
 * allow action.
 */
@Singleton
class BlockNotificationTokenStore @Inject constructor(
    private val secureStore: SecureStore,
) {
    companion object {
        private const val STORE_KEY = "blocked_domain_notification_actions"
        private const val TOKEN_TTL_MS = 24 * 60 * 60 * 1000L
        private const val MAX_TOKENS = 512
    }

    private data class Target(
        val action: String,
        val hostname: String,
        val source: String,
        val reason: String,
        val issuedAt: Long,
    )

    private val lock = Any()

    fun issue(action: String, hostname: String, source: String, reason: String): String = synchronized(lock) {
        val now = System.currentTimeMillis()
        val targets = readTargets(now)
        if (targets.size >= MAX_TOKENS) {
            targets.entries.minByOrNull { it.value.issuedAt }?.let { targets.remove(it.key) }
        }
        val token = UUID.randomUUID().toString()
        targets[token] = Target(action, hostname, source, reason, now)
        writeTargets(targets)
        token
    }

    fun consume(token: String?, action: String?, hostname: String?, source: String?, reason: String?): Boolean {
        if (token.isNullOrBlank() || action.isNullOrBlank() || hostname.isNullOrBlank()) return false
        return synchronized(lock) {
            val targets = readTargets(System.currentTimeMillis())
            val target = targets.remove(token) ?: return@synchronized false
            writeTargets(targets)
            target.action == action &&
                target.hostname.equals(hostname, ignoreCase = true) &&
                target.source == source.orEmpty() &&
                target.reason == reason.orEmpty()
        }
    }

    private fun readTargets(now: Long): MutableMap<String, Target> {
        val raw = secureStore.getString(STORE_KEY)
        if (raw.isBlank()) return linkedMapOf()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val issuedAt = item.optLong("issuedAt", 0L)
                    if (issuedAt <= 0L || now - issuedAt > TOKEN_TTL_MS) continue
                    val token = item.optString("token")
                    if (token.isBlank()) continue
                    put(
                        token,
                        Target(
                            action = item.optString("action"),
                            hostname = item.optString("hostname"),
                            source = item.optString("source"),
                            reason = item.optString("reason"),
                            issuedAt = issuedAt,
                        ),
                    )
                }
            }.toMutableMap()
        }.getOrElse { linkedMapOf() }
    }

    private fun writeTargets(targets: Map<String, Target>) {
        val array = JSONArray()
        targets.forEach { (token, target) ->
            array.put(
                JSONObject()
                    .put("token", token)
                    .put("action", target.action)
                    .put("hostname", target.hostname)
                    .put("source", target.source)
                    .put("reason", target.reason)
                    .put("issuedAt", target.issuedAt),
            )
        }
        secureStore.putString(STORE_KEY, array.toString())
    }
}
