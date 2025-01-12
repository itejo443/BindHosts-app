package me.itejo443.bindhosts

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class RunScriptTileService : TileService() {

    private val prefsFileName = "tile_prefs"
    private val tileStateKey = "tile_state"
    private val tileTimeKey = "tile_time"
    private val channelId = "bindhosts_status_channel"

    override fun onStartListening() {
        super.onStartListening()
        try {
            val (savedState) = loadTileState()
            qsTile.state = savedState
            qsTile.updateTile()
            updateNotification(getLastToastMessage())
        } catch (e: Exception) {
            Log.e("onStartListening", "Failed to start listening and restore tile state", e)
            handleError(e, "Failed to start listening and restore tile state")
        }
    }

    override fun onClick() {
        try {
            toggleTileState()
        } catch (e: Exception) {
            Log.e("onClick", "Failed to toggle tile state", e)
            handleError(e, "Failed to toggle tile state")
        }
    }

    private fun toggleTileState() {
        try {
            if (qsTile.state == Tile.STATE_INACTIVE) {
                runBindHostsScriptAndCheckStatus()
            } else {
                resetBindHosts()
            }
            checkAndUpdateBindHostsStatus()
        } catch (e: Exception) {
            Log.e("toggleTileState", "Error toggling tile state", e)
        }
    }
    
    private fun executeScript(script: String) {
		val process = ProcessBuilder("su", "-c", script).start()
        process.waitFor()
    }
    
    // In case you're calling it from other components like BroadcastReceiver
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            intent?.let {
                when (it.action) {
                    "com.itejo443.BINDHOSTS_RUN_SCRIPT" -> {
                        checkAndUpdateBindHostsStatus()
                    }
                    "com.itejo443.RESTORE_TILE_STATE" -> {
                        restoreTileStateAndNotification()
                    }
                    else -> {
                        Log.w("OnStartCommand", "Unknown action: ${it.action}")
                    }
                }
            } ?: Log.w("OnStartCommand", "Intent was null")

            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Log.e("OnStartCommandError", "Failed to process onStartCommand", e)
            super.onStartCommand(intent, flags, startId)
        }
    }
    
    private fun runBindHostsScriptAndCheckStatus() {
        try {
			executeScript("sh /data/adb/modules/bindhosts/bindhosts.sh --action")
        } catch (e: Exception) {
			handleError(e, "Error while activating bindhosts status")
        }
    }
    
    private fun resetBindHosts() {
        try {
	    	executeScript("sh /data/adb/modules/bindhosts/bindhosts.sh --force-reset")
        } catch (e: Exception) {
		    handleError(e, "Error while resetting bindhosts status")
       }
    }

    fun checkAndUpdateBindHostsStatus() {
        try {
            val process = ProcessBuilder("su", "-c", "sh /data/adb/modules/bindhosts/bindhosts.sh").start()
            val reader = process.inputStream.bufferedReader()
            val lines = reader.readLines()
            process.waitFor()

            val newState = determineNewState(lines)
            
            if (qsTile.state != newState) {
                qsTile.state = newState
                val currentTime = getCurrentTime()
                qsTile.updateTile()
                saveTileState(newState, currentTime)
                updateNotification(getLastToastMessage())
            } else {
                updateNotification(getLastToastMessage())
            }

        } catch (e: Exception) {
            Log.e("checkAndUpdateBindHostsStatus", "Error checking bindhosts status", e)
            handleError(e, "Error checking bindhosts status")
        }
    }

    private fun determineNewState(lines: List<String>): Int {
        return try {
            when {
				lines.size >= 5 && lines[4].contains("blocked: 0", ignoreCase = true) -> {
					showToast("NET_BIND_SERVICE | NET_RAW")
					showToast("Optional : DAC_OVERRIDE | SYS_PTRACE")
					showToast("Enable SU with Capabilities (KernelSU)")
                    Tile.STATE_INACTIVE
				}
                lines.size >= 5 && lines[4].contains("adaway", ignoreCase = true) -> {
                    showToast("Incompatible with AdAway")
                    Tile.STATE_INACTIVE
                }
                lines.size >= 5 && lines[4].contains("active", ignoreCase = true) -> {
					showToast("bindhosts: active")
                    Tile.STATE_ACTIVE
                }
                lines.size >= 5 && lines[4].contains("reset", ignoreCase = true) -> {
                    showToast("bindhosts: reset")
                    Tile.STATE_INACTIVE
                }
                lines.size >= 5 && lines[4].contains("ready", ignoreCase = true) -> {
                    showToast("Start using bindhosts")
                    Tile.STATE_INACTIVE
                }
                else -> {
                    showToast("Something went wrong")
                    Tile.STATE_INACTIVE
                }
            }
        } catch (e: Exception) {
			Log.e("checkAndUpdateBindHostsStatus", "Error checking bindhosts status", e)
            handleError(e, "Error checking bindhosts status")
            Tile.STATE_INACTIVE
        }
    }

    private fun handleError(e: Exception, message: String) {
        Log.e("BindHosts", message, e)
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
        showToast("Enable SU for BindHosts-app")
        updateNotification(getLastToastMessage())
        saveTileState(qsTile.state, getCurrentTime())
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("last_toast_message", message).apply()
        } catch (e: Exception) {
            Log.e("ShowToastError", "Failed to show toast message: $message", e)
        }
    }

    private fun getLastToastMessage(): String {
        return try {
            getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
                .getString("last_toast_message", "") ?: ""
        } catch (e: Exception) {
            Log.e("getLastToastMessage", "Error fetching last toast message", e)
            ""
            }
    }

    private fun saveTileState(state: Int, time: String) {
        try {
            val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            sharedPreferences.edit().putInt(tileStateKey, state).putString(tileTimeKey, time).apply()
        } catch (e: Exception) {
            Log.e("SaveTileStateError", "Failed to save tile state", e)
        }
    }

    private fun loadTileState(): Pair<Int, String> {
        return try {
            val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            val state = sharedPreferences.getInt(tileStateKey, Tile.STATE_INACTIVE)
            val time = sharedPreferences.getString(tileTimeKey, "") ?: ""
            Pair(state, time)
        } catch (e: Exception) {
            Log.e("LoadTileStateError", "Failed to load tile state", e)
            Pair(Tile.STATE_INACTIVE, "")
        }
    }

    private fun getCurrentTime(): String {
        return try {
            val sdf = SimpleDateFormat("dd MMM | HH:mm:ss", Locale.getDefault())
            sdf.format(Date())
        } catch (e: Exception) {
            Log.e("GetCurrentTimeError", "Failed to get current time", e)
            "N/A"
        }
    }
    
    private fun restoreTileStateAndNotification() {
        try {
            val savedState = loadTileState()
            qsTile.state = savedState.first
            qsTile.updateTile()
            updateNotification(getLastToastMessage())
        } catch (e: Exception) {
            Log.e("RestoreTileStateError", "Failed to restore tile state", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Bindhosts Status", NotificationManager.IMPORTANCE_LOW)
                channel.description = "Shows the current Bindhosts status"
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e("CreateNotificationError", "Failed to create notification channel", e)
        }
    }

    private fun updateNotification(lastToastMessage: String) {
        try {
            createNotificationChannel()

            // Fetch saved state and time inside the method
            val (state, timestamp) = loadTileState()

            val statusText = if (state == Tile.STATE_ACTIVE) {
                "active: $timestamp\n$lastToastMessage"
            } else {
                "reset: $timestamp\n$lastToastMessage"
            }

            val notificationIntent = Intent(this, NotificationClickReceiver::class.java).apply {
                action = "com.itejo443.BINDHOSTS_NOTIFICATION_CLICKED"
            }

            val pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val isLightStatusBar = isLightStatusBar(this)
            val notificationIcon = if (isLightStatusBar) R.drawable.ic_launcher_dark else R.drawable.ic_launcher_light

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("BindHosts Status")
                .setContentText(statusText)
                .setSmallIcon(notificationIcon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, notification)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("updateNotification", "Error updating notification", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun isLightStatusBar(context: Context): Boolean {
        return try {
            val activity = context as? Activity ?: return false
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val window = activity.window
                    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                    windowInsetsController.isAppearanceLightStatusBars == true
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    val decorView = activity.window.decorView
                    decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("isLightStatusBar", "Error checking light status bar", e)
            false
        }
    }
}
