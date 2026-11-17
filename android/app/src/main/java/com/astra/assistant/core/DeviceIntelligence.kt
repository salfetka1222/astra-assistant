package com.astra.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import com.astra.assistant.AstraAccessibilityService

/** No background scans, no QUERY_ALL_PACKAGES, no identifier collection, no persistence. */
object DeviceIntelligence {
    fun read(context: Context): DeviceSnapshot {
        val battery = runCatching {
            val service = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            service.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        }.getOrNull()
        val powerSave = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrNull()
        val ram = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.availMem / (1024L * 1024L)
        }.getOrNull()
        val storage = runCatching {
            StatFs(Environment.getDataDirectory().absolutePath).availableBytes / (1024L * 1024L)
        }.getOrNull()
        fun canOpen(action: String): Boolean? = runCatching {
            Intent(action).resolveActivity(context.packageManager) != null
        }.getOrNull()
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return DeviceSnapshot(
            model = model,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            batteryPercent = battery,
            powerSaveMode = powerSave,
            availableRamMiB = ram,
            availableStorageMiB = storage,
            accessibilityRunning = AstraAccessibilityService.isRunning(),
            cameraAvailable = canOpen(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            alarmsAvailable = canOpen(AlarmClock.ACTION_SHOW_ALARMS),
            dialerAvailable = canOpen(Intent.ACTION_DIAL)
        )
    }

    fun report(context: Context): String = DeviceIntelligenceReport.format(read(context))
}
