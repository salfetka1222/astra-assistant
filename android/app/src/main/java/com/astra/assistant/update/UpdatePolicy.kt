package com.astra.assistant.update

import java.net.URI

internal object UpdatePolicy {
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    fun version(tag: String): Long = Regex("^astra-v([1-9][0-9]*)$")
        .matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull()
        ?: error("Неподдерживаемый номер релиза.")

    fun assetUrl(url: String, tag: String, file: String): String {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "github.com" &&
            uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.rawPath == "/salfetka1222/astra-assistant/releases/download/$tag/$file") {
            "Недопустимый адрес файла обновления."
        }
        return url
    }

    fun trustedDownload(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host in setOf("api.github.com", "raw.githubusercontent.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)

    fun checksum(text: String): String =
        Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?app-release\\.apk$")
            .matchEntire(text.trim())?.groupValues?.get(1)?.lowercase()
            ?: error("Некорректная контрольная сумма обновления.")

    fun validateIdentity(expectedPackage: String, actualPackage: String, installed: Long,
                         expectedVersion: Long, actualVersion: Long,
                         installedSigners: Set<String>, archiveSigners: Set<String>) {
        require(actualPackage == expectedPackage) { "APK предназначен для другого приложения." }
        require(actualVersion == expectedVersion && actualVersion > installed) {
            "Версия APK не соответствует обновлению."
        }
        require(installedSigners.isNotEmpty() && installedSigners == archiveSigners) {
            "Подпись обновления не совпадает с установленной Astra."
        }
    }
}
