package com.tvapp.livetv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun parseManifest_legacyRootOnly_returnsRootData() {
        val json = """
            {
              "package": "com.tvapp.livetv",
              "versionCode": 180,
              "versionName": "0.1.180",
              "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
              "sha256": "ABCDEF123456",
              "mandatory": true
            }
        """.trimIndent()

        val update = AppUpdateManager.parseManifest(json, "com.tvapp.livetv")

        assertEquals(180, update.versionCode)
        assertEquals("0.1.180", update.versionName)
        assertEquals("https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk", update.apkUrl)
        assertEquals("abcdef123456", update.sha256)
        assertTrue(update.mandatory)
    }

    @Test
    fun parseManifest_multiPackage_returnsTvDataForTvPackage() {
        val json = """
            {
              "package": "com.tvapp.livetv",
              "versionCode": 182,
              "versionName": "0.1.182",
              "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
              "sha256": "ROOT_SHA",
              "packages": {
                "com.tvapp.livetv": {
                  "versionCode": 182,
                  "versionName": "0.1.182",
                  "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
                  "sha256": "TV_SHA"
                },
                "com.tvapp.mobile": {
                  "versionCode": 182,
                  "versionName": "0.1.182-mobile",
                  "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp-Mobile.apk",
                  "sha256": "MOBILE_SHA"
                }
              }
            }
        """.trimIndent()

        val update = AppUpdateManager.parseManifest(json, "com.tvapp.livetv")

        assertEquals(182, update.versionCode)
        assertEquals("0.1.182", update.versionName)
        assertEquals("https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk", update.apkUrl)
        assertEquals("tv_sha", update.sha256)
        assertFalse(update.mandatory)
    }

    @Test
    fun parseManifest_multiPackage_returnsMobileDataForMobilePackage() {
        val json = """
            {
              "package": "com.tvapp.livetv",
              "versionCode": 182,
              "versionName": "0.1.182",
              "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
              "sha256": "ROOT_SHA",
              "packages": {
                "com.tvapp.livetv": {
                  "versionCode": 182,
                  "versionName": "0.1.182",
                  "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
                  "sha256": "TV_SHA"
                },
                "com.tvapp.mobile": {
                  "versionCode": 182,
                  "versionName": "0.1.182-mobile",
                  "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp-Mobile.apk",
                  "sha256": "MOBILE_SHA"
                }
              }
            }
        """.trimIndent()

        val update = AppUpdateManager.parseManifest(json, "com.tvapp.mobile")

        assertEquals(182, update.versionCode)
        assertEquals("0.1.182-mobile", update.versionName)
        assertEquals("https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp-Mobile.apk", update.apkUrl)
        assertEquals("mobile_sha", update.sha256)
        assertFalse(update.mandatory)
    }

    @Test
    fun parseManifest_packageNotFoundInPackages_fallsBackToRoot() {
        val json = """
            {
              "package": "com.tvapp.livetv",
              "versionCode": 182,
              "versionName": "0.1.182",
              "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
              "sha256": "FALLBACK_SHA",
              "packages": {
                "com.tvapp.livetv": {
                  "versionCode": 182,
                  "versionName": "0.1.182",
                  "apkUrl": "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk",
                  "sha256": "TV_SHA"
                }
              }
            }
        """.trimIndent()

        val update = AppUpdateManager.parseManifest(json, "com.tvapp.unknown")

        assertEquals(182, update.versionCode)
        assertEquals("https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/TVApp.apk", update.apkUrl)
        assertEquals("fallback_sha", update.sha256)
    }
}
