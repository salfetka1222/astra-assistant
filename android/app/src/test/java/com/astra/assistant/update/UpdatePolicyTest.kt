package com.astra.assistant.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun rejectsInvalidVersions() {
        for (tag in listOf("android-debug", "astra-v0", "astra-v-1", "astra-v999999999999999999999999")) {
            assertTrue(runCatching { UpdatePolicy.version(tag) }.isFailure)
        }
        assertEquals(3L, UpdatePolicy.version("astra-v3"))
    }

    @Test fun acceptsOnlyExactRepositoryAssets() {
        val good = "https://github.com/salfetka1222/astra-assistant/releases/download/astra-v3/app-release.apk"
        assertEquals(good, UpdatePolicy.assetUrl(good, "astra-v3", "app-release.apk"))
        for (bad in listOf(good.replace("https:", "http:"), good.replace("github.com", "github.com.evil.test"),
            good.replace("salfetka1222", "other"), "$good?redirect=evil", good.replace("astra-v3", "astra-v2"))) {
            assertTrue(runCatching { UpdatePolicy.assetUrl(bad, "astra-v3", "app-release.apk") }.isFailure)
        }
    }

    @Test fun restrictsRedirectDestinations() {
        assertTrue(UpdatePolicy.trustedDownload("https://release-assets.githubusercontent.com/file?token=x"))
        for (url in listOf("http://github.com/file", "https://github.com.evil.test/file",
            "https://user@github.com/file", "https://127.0.0.1/file", "https://github.com:8443/file")) {
            assertFalse(UpdatePolicy.trustedDownload(url))
        }
    }

    @Test fun bindsChecksumToApkFilename() {
        val hash = "ab".repeat(32)
        assertEquals(hash, UpdatePolicy.checksum("$hash  app-release.apk\n"))
        assertTrue(runCatching { UpdatePolicy.checksum("$hash  other.apk") }.isFailure)
        assertTrue(runCatching { UpdatePolicy.checksum("abc  app-release.apk") }.isFailure)
        assertTrue(runCatching { UpdatePolicy.checksum("$hash  app-release.apk\n$hash  app-release.apk") }.isFailure)
    }

    @Test fun rejectsWrongPackageVersionAndSigner() {
        fun check(pkg: String = "astra", version: Long = 3, signer: Set<String> = setOf("trusted")) =
            UpdatePolicy.validateIdentity("astra", pkg, 2, 3, version, setOf("trusted"), signer)
        check()
        assertTrue(runCatching { check(pkg = "other") }.isFailure)
        assertTrue(runCatching { check(version = 2) }.isFailure)
        assertTrue(runCatching { check(version = 4) }.isFailure)
        assertTrue(runCatching { check(signer = setOf("unknown")) }.isFailure)
        assertTrue(runCatching { check(signer = emptySet()) }.isFailure)
        assertTrue(runCatching {
            UpdatePolicy.validateIdentity("astra", "astra", 3, 3, 3, setOf("trusted"), setOf("trusted"))
        }.isFailure)
    }
}
