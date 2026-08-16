package com.example.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

/**
 * 負責執行 Mock Location 的 Foreground Service。
 * 在 Android 中，使用 LocationManager.setTestProviderLocation 需要
 * 1. 宣告並取得 ACCESS_MOCK_LOCATION 權限
 * 2. 系統開發者選項內需將本 App 設為「模擬位置應用程式」
 */
class MockLocationService : Service() {

    private val CHANNEL_ID = "MockLocationServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "MockLocationService"

    private lateinit var locationManager: LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    @Volatile
    private var isMocking = false
    private var mockThread: Thread? = null

    // 接收從 Activity 傳來的經緯度
    private var targetLat: Double = 0.0
    private var targetLng: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 從 Intent 取得欲模擬的經緯度
        targetLat = intent?.getDoubleExtra("LAT", 0.0) ?: 0.0
        targetLng = intent?.getDoubleExtra("LNG", 0.0) ?: 0.0

        Log.d(TAG, "onStartCommand: 啟動 Mock Location, 座標: $targetLat, $targetLng")

        // 啟動 Foreground Service 的 Notification
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("虛擬定位執行中")
                .setContentText("座標: $targetLat, $targetLng")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("虛擬定位執行中")
                .setContentText("座標: $targetLat, $targetLng")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build()
        }

        startForeground(NOTIFICATION_ID, notification)

        // 設定 Test Provider，如果失敗則停止服務並提示使用者
        if (!setupMockProvider()) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                android.widget.Toast.makeText(applicationContext, "請先在「開發人員選項」中將此APP設為「模擬位置應用程式」", android.widget.Toast.LENGTH_LONG).show()
            }
            stopSelf()
            return START_NOT_STICKY
        }

        // 先停止之前的推播，避免 Thread leak
        stopMockingThread()

        // 啟動每秒一次的推播執行緒
        startMockingThread()

        // 使用 START_REDELIVER_INTENT 確保重啟時能收到先前的 Intent 與座標
        return START_REDELIVER_INTENT
    }

    private fun setupMockProvider(): Boolean {
        return try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

            for (provider in providers) {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (e: Exception) {
                    // provider 不存在，忽略
                }

                locationManager.addTestProvider(
                    provider,
                    false, false, false, false, true,
                    true, true, 0, 5
                )
                locationManager.setTestProviderEnabled(provider, true)
                Log.d(TAG, "Test provider ($provider) added and enabled.")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: 尚未在開發者選項中將此App設為模擬位置應用程式", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException: Provider 已經存在或其他參數錯誤", e)
            false
        }
    }

    private fun startMockingThread() {
        isMocking = true
        mockThread = Thread {
            while (isMocking) {
                try {
                    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    val currentTime = System.currentTimeMillis()
                    val currentRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                    for (provider in providers) {
                        val mockLocation = Location(provider).apply {
                            latitude = targetLat
                            longitude = targetLng
                            altitude = 0.0
                            time = currentTime
                            accuracy = 1f // 高精確度
                            elapsedRealtimeNanos = currentRealtimeNanos
                        }

                        // 推播假座標
                        locationManager.setTestProviderLocation(provider, mockLocation)
                        Log.d(TAG, "Pushing mock location to $provider: $targetLat, $targetLng")
                    }

                    // 暫停 1 秒
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error pushing mock location: ${e.message}")
                    break
                }
            }
        }
        mockThread?.start()
    }

    private fun stopMockingThread() {
        if (isMocking || mockThread != null) {
            Log.d(TAG, "stopMockingThread: 停止先前的推播迴圈")
            isMocking = false
            mockThread?.interrupt()
            mockThread = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: 停止 Mock Location")

        // 停止推播迴圈
        stopMockingThread()

        // 必須確實呼叫 removeTestProvider 釋放 Provider，恢復真實 GPS
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                locationManager.removeTestProvider(provider)
                Log.d(TAG, "Test provider $provider removed. Real location restored.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing test provider: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 不需要 bind
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Mock Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
