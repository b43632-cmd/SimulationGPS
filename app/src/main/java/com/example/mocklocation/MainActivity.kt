package com.example.mocklocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var selectedMarker: Marker? = null
    private var selectedLatLng: LatLng? = null

    private val PERMISSIONS_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 檢查並請求所需權限
        checkAndRequestPermissions()

        // 初始化 Google Map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val btnStartMock: Button = findViewById(R.id.btnStartMock)
        val btnStopMock: Button = findViewById(R.id.btnStopMock)

        // 開始打卡定位
        btnStartMock.setOnClickListener {
            val target = selectedLatLng
            if (target != null) {
                startMockLocationService(target.latitude, target.longitude)
                Toast.makeText(this, "開始模擬定位：${target.latitude}, ${target.longitude}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "請先在地圖上點選一個位置", Toast.LENGTH_SHORT).show()
            }
        }

        // 停止定位
        btnStopMock.setOnClickListener {
            stopMockLocationService()
            Toast.makeText(this, "停止模擬定位，恢復真實GPS", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // 預設視角設定在使用者的指定位置
        val defaultLocation = LatLng(22.730522, 120.321092)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))

        // 點擊地圖時放置或移動 Marker，並記錄經緯度
        map.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            if (selectedMarker == null) {
                selectedMarker = map.addMarker(MarkerOptions().position(latLng).title("打卡位置"))
            } else {
                selectedMarker?.position = latLng
            }
        }
    }

    private fun startMockLocationService(lat: Double, lng: Double) {
        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra("LAT", lat)
            putExtra("LNG", lng)
        }

        // 依照 Android 版本決定如何啟動 Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMockLocationService() {
        val intent = Intent(this, MockLocationService::class.java)
        stopService(intent)
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 基本位置權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Android 13+ 需要通知權限才能顯示 Foreground Service Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 權限已全部取得
            } else {
                Toast.makeText(this, "需要位置權限與通知權限才能正常運作", Toast.LENGTH_LONG).show()
            }
        }
    }
}
