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

        // 設定 Test Provider
        setupMockProvider()

        // 先停止之前的推播，避免 Thread leak
        stopMockingThread()

        // 啟動每秒一次的推播執行緒
        startMockingThread()

        // 使用 START_REDELIVER_INTENT 確保重啟時能收到先前的 Intent 與座標
        return START_REDELIVER_INTENT
    }

    private fun setupMockProvider() {
        try {
            // 如果 provider 已存在先移除再加入 (避免之前的異常狀態)
            try {
                locationManager.removeTestProvider(providerName)
            } catch (e: Exception) {
                // provider 不存在，忽略
            }

            // 加入 Test Provider
            // Android 12 (API 31)+ 之後可以使用 createProviderPropertiesBuilder 等
            // 這裡為了向下相容至 API 21+ 通常需給定各種布林值與精確度
            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true,
                true, true, 0, 5
            )
            locationManager.setTestProviderEnabled(providerName, true)
            Log.d(TAG, "Test provider ($providerName) added and enabled.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: 尚未在開發者選項中將此App設為模擬位置應用程式", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException: Provider '$providerName' 已經存在或其他參數錯誤", e)
        }
    }

    private fun startMockingThread() {
        isMocking = true
        mockThread = Thread {
            while (isMocking) {
                try {
                    val mockLocation = Location(providerName).apply {
                        latitude = targetLat
                        longitude = targetLng
                        altitude = 0.0
                        time = System.currentTimeMillis()
                        accuracy = 1f // 高精確度
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }

                    // 推播假座標
                    locationManager.setTestProviderLocation(providerName, mockLocation)
                    Log.d(TAG, "Pushing mock location: $targetLat, $targetLng")

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
            locationManager.removeTestProvider(providerName)
            Log.d(TAG, "Test provider removed. Real GPS restored.")
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
