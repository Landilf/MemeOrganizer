package ru.landilf.memeorganizer.data.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the sticky system battery broadcast without requiring a runtime permission. */
@Singleton
class BatteryStateChecker @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun needsHeavyWorkConfirmation(): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return false
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_LEVEL)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_SCALE)
        if (level < 0 || scale <= 0) return false
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return !charging && level * PERCENT_MULTIPLIER < scale * LOW_BATTERY_PERCENT
    }

    private companion object {
        const val UNKNOWN_LEVEL = -1
        const val UNKNOWN_SCALE = -1
        const val PERCENT_MULTIPLIER = 100
        const val LOW_BATTERY_PERCENT = 20
    }
}
