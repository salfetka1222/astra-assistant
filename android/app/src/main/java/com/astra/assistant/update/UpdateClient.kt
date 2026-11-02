package com.astra.assistant.update

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class AstraRelease(val code: Long, val apkUrl: String, val checksumUrl: String, val size: Long)
internal data class ReadyUpdate(val release: AstraRelease, val file: File, val sha256: String)

internal class UpdateClient(private val context: Context) {
    companion object {
        private const val API = "https://api.github.com/repos/salfetka1222/astra-assistant/releases/latest"
        private const val TEST_API = "https://api.github.com/repos/salfetka1222/astra-assistant/releases/tags/astra-v16"
    }

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun connection(address: String): HttpURLConnection {
        var next = address
        repeat(6) {
            require(UpdatePolicy.trustedDownload(next)) { "Небезопасный адрес загрузки." }
            val connection = (URL(next).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Astra-Assistant-Updater")
            }
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location") ?: error("Нет адреса перенаправления.")
                next = URL(URL(next), location).toString()
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
        error("Слишком много перенаправлений.")
    }

    private suspend fun readText(address: String, maxBytes: Int, allowMissing: Boolean = false): String? {
        val c = connection(address)
        try {
            if (allowMissing && c.responseCode == 404) return null
            require(c.responseCode == 200) { "Сервер обновлений ответил HTTP ${c.responseCode}." }
            return c.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxBytes) { "Ответ сервера слишком большой." }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { c.disconnect() }
    }

    suspend fun latest(testChannel: Boolean = false): AstraRelease? {
        val text = readText(if (testChannel) TEST_API else API, 1024 * 1024, true) ?: return null
        val json = JSONObject(text)
        require(!json.optBoolean("draft") && (testChannel || !json.optBoolean("prerelease"))) { "Релиз ещё не готов." }
        if (testChannel) require(json.optBoolean("prerelease") && json.getString("tag_name") == "astra-v16") {
            "Неверный тестовый релиз."
        }
        val tag = json.getString("tag_name")
        val version = UpdatePolicy.version(tag)
        val installed = code(context.packageManager.getPackageInfo(context.packageName, 0))
        if (version <= installed) return null
        val assets = json.getJSONArray("assets")
        fun asset(name: String): JSONObject {
            var found: JSONObject? = null
            for (index in 0 until assets.length()) {
                val candidate = assets.getJSONObject(index)
                if (candidate.optString("name") == name) {
                    require(found == null) { "Повторяющийся файл релиза." }
                    found = candidate
                }
            }
            return found ?: error("В релизе отсутствует $name.")
        }
        val apk = asset("app-release.apk")
        val checksum = asset("app-release.apk.sha256")
        val size = apk.getLong("size")
        require(size in 1..UpdatePolicy.MAX_APK_BYTES) { "Недопустимый размер APK." }
        return AstraRelease(version,
            UpdatePolicy.assetUrl(apk.getString("browser_download_url"), tag, "app-release.apk"),
            UpdatePolicy.assetUrl(checksum.getString("browser_download_url"), tag, "app-release.apk.sha256"),
            size)
    }

    suspend fun download(release: AstraRelease): ReadyUpdate {
        val expected = UpdatePolicy.checksum(readText(release.checksumUrl, 1024)!!)
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        // A unique file keeps simultaneous screens from modifying a verified APK.
        val part = File.createTempFile("astra-", ".apk", folder)
        var complete = false
        try {
            val c = connection(release.apkUrl)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                require(c.responseCode == 200) { "Не удалось скачать APK: HTTP ${c.responseCode}." }
                c.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= release.size && total <= UpdatePolicy.MAX_APK_BYTES) {
                                "Размер загрузки превышает ожидаемый."
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        require(total == release.size) { "APK скачан не полностью. Повтори загрузку." }
                    }
                }
            } finally { c.disconnect() }
            require(hex(digest.digest()) == expected) { "Контрольная сумма не совпала. APK отклонён." }
            val ready = ReadyUpdate(release, part, expected)
            verify(ready)
            complete = true
            return ready
        } finally { if (!complete) part.delete() }
    }

    @Suppress("DEPRECATION")
    suspend fun verify(ready: ReadyUpdate) {
        require(ready.file.isFile && ready.file.length() == ready.release.size) { "Скачанный APK недоступен." }
        val digest = MessageDigest.getInstance("SHA-256")
        ready.file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(hex(digest.digest()) == ready.sha256) { "Скачанный APK изменён. Скачай обновление заново." }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(ready.file.absolutePath, flags)
            ?: error("Не удалось прочитать APK.")
        fun signers(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet() ?: emptySet()
        }
        UpdatePolicy.validateIdentity(context.packageName, archive.packageName, code(installed),
            ready.release.code, code(archive), signers(installed), signers(archive))
    }

    fun requestInstallPermission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")))
    }

    fun install(ready: ReadyUpdate) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Astra update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
