package com.hostshield.util

import com.hostshield.data.source.BoundedResponseReader
import okhttp3.Credentials as OkHttpCredentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Roadmap #37 — WebDAV cloud sync for privacy-friendly backup.
 *
 * All operations are synchronous; callers should wrap in a coroutine scope.
 * Thread-safe because every method is stateless on the injected [OkHttpClient].
 */
@Singleton
class WebDavSync @Inject constructor(
    private val httpClient: OkHttpClient
) {
    // ── Data classes ────────────────────────────────────────────

    data class Credentials(
        val username: String,
        val password: String
    )

    data class RemoteFile(
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: String,
        val isDirectory: Boolean
    )

    sealed class SyncResult {
        object Success : SyncResult()
        data class NetworkError(val message: String) : SyncResult()
        object AuthError : SyncResult()
        data class ServerError(val code: Int) : SyncResult()
        object ParseError : SyncResult()
    }

    // ── Low-level WebDAV operations ─────────────────────────────

    /**
     * Upload [data] via HTTP PUT to [remotePath] on [serverUrl].
     */
    fun upload(
        serverUrl: String,
        credentials: Credentials,
        remotePath: String,
        data: ByteArray
    ): Boolean {
        return try {
            val url = buildUrl(serverUrl, remotePath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .put(data.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code in 200..299
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Download the resource at [remotePath] via HTTP GET.
     * Returns `null` on any failure.
     */
    fun download(
        serverUrl: String,
        credentials: Credentials,
        remotePath: String
    ): ByteArray? {
        return try {
            val url = buildUrl(serverUrl, remotePath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body.byteStream().use { stream ->
                        BoundedInputReader.readBytes(stream, MAX_WEBDAV_DOWNLOAD_BYTES, "WebDAV download")
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * List files/directories under [remotePath] using PROPFIND (Depth: 1).
     */
    fun listFiles(
        serverUrl: String,
        credentials: Credentials,
        remotePath: String
    ): List<RemoteFile>? {
        return try {
            val url = buildUrl(serverUrl, remotePath)
            val propfindBody = """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                    <d:resourcetype/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .header("Depth", "1")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 207 || response.isSuccessful) {
                    val xml = BoundedResponseReader.readUtf8(
                        response,
                        MAX_WEBDAV_PROPFIND_BYTES,
                        "WebDAV PROPFIND"
                    ).content
                    parsePropfindResponse(xml, remotePath)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Delete the resource at [remotePath] via HTTP DELETE.
     */
    fun delete(
        serverUrl: String,
        credentials: Credentials,
        remotePath: String
    ): Boolean {
        return try {
            val url = buildUrl(serverUrl, remotePath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .delete()
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code in 200..299
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Create a directory (collection) at [remotePath] via MKCOL.
     */
    fun createDirectory(
        serverUrl: String,
        credentials: Credentials,
        remotePath: String
    ): Boolean {
        return try {
            val url = buildUrl(serverUrl, remotePath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .method("MKCOL", null)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code in 200..299 || response.code == 405 // 405 = already exists
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Test whether [serverUrl] is reachable and [credentials] are valid
     * by issuing a PROPFIND with Depth 0 on the root.
     */
    fun testConnection(
        serverUrl: String,
        credentials: Credentials
    ): Boolean {
        return try {
            val normalizedServerUrl = normalizeServerUrl(serverUrl)
            val request = Request.Builder()
                .url("$normalizedServerUrl/")
                .header("Authorization", basicAuth(credentials))
                .header("Depth", "0")
                .method("PROPFIND", null)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code == 207 || response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    // ── Higher-level sync methods ───────────────────────────────

    /**
     * Upload a backup to `/HostShield/backups/backup_<timestamp>.json`.
     * Creates the directory tree if it does not already exist.
     */
    fun syncBackup(
        serverUrl: String,
        credentials: Credentials,
        backupData: ByteArray
    ): SyncResult {
        return try {
            // Ensure directory structure exists
            createDirectory(serverUrl, credentials, SYNC_ROOT)
            createDirectory(serverUrl, credentials, BACKUPS_DIR)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val remotePath = "$BACKUPS_DIR/backup_$timestamp.json"

            val url = buildUrl(serverUrl, remotePath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .put(backupData.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                mapResponseToResult(response.code)
            }
        } catch (e: Exception) {
            SyncResult.NetworkError(e.message ?: "Unknown network error")
        }
    }

    /**
     * Download the most recent backup from the server.
     * Returns the [SyncResult] together with the raw bytes (null on failure).
     */
    fun fetchLatestBackup(
        serverUrl: String,
        credentials: Credentials
    ): Pair<SyncResult, ByteArray?> {
        return try {
            val files = listFiles(serverUrl, credentials, BACKUPS_DIR)
                ?: return Pair(SyncResult.NetworkError("Failed to list remote backups"), null)
            val backups = files.filter { !it.isDirectory && it.name.endsWith(".json") }

            if (backups.isEmpty()) {
                return Pair(SyncResult.NetworkError("No backups found"), null)
            }

            // Sort by name descending — timestamp in the filename ensures correct order
            val latest = backups.sortedByDescending { it.name }.first()
            val data = download(serverUrl, credentials, latest.path)

            if (data != null) {
                Pair(SyncResult.Success, data)
            } else {
                Pair(SyncResult.NetworkError("Failed to download backup"), null)
            }
        } catch (e: Exception) {
            Pair(SyncResult.NetworkError(e.message ?: "Unknown error"), null)
        }
    }

    /**
     * List all backups stored on the server.
     */
    fun listBackups(
        serverUrl: String,
        credentials: Credentials
    ): Pair<SyncResult, List<RemoteFile>> {
        return try {
            val url = buildUrl(serverUrl, BACKUPS_DIR)
            val propfindBody = """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                    <d:resourcetype/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", basicAuth(credentials))
                .header("Depth", "1")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val result = mapResponseToResult(response.code)
                if (result is SyncResult.Success || response.code == 207) {
                    val xml = BoundedResponseReader.readUtf8(
                        response,
                        MAX_WEBDAV_PROPFIND_BYTES,
                        "WebDAV PROPFIND"
                    ).content
                    val files = parsePropfindResponse(xml, BACKUPS_DIR)
                        .filter { !it.isDirectory && it.name.endsWith(".json") }
                    Pair(SyncResult.Success, files)
                } else {
                    Pair(result, emptyList())
                }
            }
        } catch (e: Exception) {
            Pair(SyncResult.NetworkError(e.message ?: "Unknown error"), emptyList())
        }
    }

    // ── Private helpers ─────────────────────────────────────────

    private fun basicAuth(credentials: Credentials): String =
        OkHttpCredentials.basic(credentials.username, credentials.password)

    private fun buildUrl(serverUrl: String, remotePath: String): String {
        val base = normalizeServerUrl(serverUrl)
        // Validate traversal on the DECODED segments (so `%2e%2e` can't sneak a
        // `..` past the check), but build the request from the ORIGINAL path so
        // percent-encoded or `+`-bearing filenames from server PROPFIND hrefs are
        // not corrupted by form-decoding.
        val decodedPath = try {
            java.net.URLDecoder.decode(remotePath, "UTF-8")
        } catch (_: Exception) { remotePath }.trimStart('/')
        require(!decodedPath.split('/').any { it == ".." || it == "." }) {
            "Path traversal segments rejected: $decodedPath"
        }
        val path = remotePath.trimStart('/')
        return "$base/$path"
    }

    private fun mapResponseToResult(code: Int): SyncResult = when {
        code in 200..299 || code == 207 -> SyncResult.Success
        code == 401 || code == 403 -> SyncResult.AuthError
        code >= 500 -> SyncResult.ServerError(code)
        else -> SyncResult.ServerError(code)
    }

    /**
     * Parse a PROPFIND multi-status XML response into [RemoteFile] entries.
     * Skips the collection entry that matches [parentPath] itself.
     */
    private fun parsePropfindResponse(xml: String, parentPath: String): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var href: String? = null
            var contentLength: Long = 0
            var lastModified = ""
            var isDirectory = false
            var insideResponse = false
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name
                        currentTag = localName
                        when (localName) {
                            "response" -> {
                                insideResponse = true
                                href = null
                                contentLength = 0
                                lastModified = ""
                                isDirectory = false
                            }
                            "collection" -> {
                                if (insideResponse) isDirectory = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideResponse) {
                            val text = parser.text?.trim() ?: ""
                            when (currentTag) {
                                "href" -> href = text
                                "getcontentlength" -> contentLength = text.toLongOrNull() ?: 0
                                "getlastmodified" -> lastModified = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "response" && insideResponse) {
                            insideResponse = false
                            val path = href ?: ""
                            val normalizedParent = parentPath.trimEnd('/')
                            val normalizedHref = path.trimEnd('/')

                            // Skip the parent collection itself and reject path traversal
                            val hasTraversal = normalizedHref.split('/').any { it == ".." || it == "." }
                            if (normalizedHref.isNotEmpty() &&
                                !normalizedHref.endsWith(normalizedParent) &&
                                !hasTraversal
                            ) {
                                val name = normalizedHref.substringAfterLast('/')
                                if (name.isNotEmpty()) {
                                    files.add(
                                        RemoteFile(
                                            name = name,
                                            path = path,
                                            size = contentLength,
                                            lastModified = lastModified,
                                            isDirectory = isDirectory
                                        )
                                    )
                                }
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Return whatever we managed to parse
        }
        return files
    }

    companion object {
        private const val SYNC_ROOT = "/HostShield"
        private const val BACKUPS_DIR = "/HostShield/backups"
        private const val MAX_WEBDAV_DOWNLOAD_BYTES = 25L * 1024L * 1024L
        private const val MAX_WEBDAV_PROPFIND_BYTES = 1L * 1024L * 1024L

        internal fun normalizeServerUrl(rawServerUrl: String): String {
            val url = rawServerUrl.trim().toHttpUrl()
            require(url.isHttps) { "WebDAV server URL must use HTTPS." }
            require(url.username.isEmpty() && url.password.isEmpty()) {
                "WebDAV credentials must be entered separately."
            }
            return url.toString().trimEnd('/')
        }

        internal fun normalizedServerUrlOrNull(rawServerUrl: String): String? =
            runCatching { normalizeServerUrl(rawServerUrl) }.getOrNull()
    }
}
