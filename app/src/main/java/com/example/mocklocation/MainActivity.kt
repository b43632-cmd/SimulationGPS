package com.example.mocklocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
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

    private lateinit var sharedPreferences: SharedPreferences
    private val PREF_NAME = "MockLocations"
    private val KEY_LOCATIONS = "saved_locations"

    data class SavedLocation(val name: String, val lat: Double, val lng: Double)

    private val savedLocationsList = mutableListOf<SavedLocation>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 檢查並請求所需權限
        checkAndRequestPermissions()

        // 載入已儲存的地點
        loadSavedLocations()

        // 初始化 Google Map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val btnStartMock: Button = findViewById(R.id.btnStartMock)
        val btnStopMock: Button = findViewById(R.id.btnStopMock)

        val etLatitude: EditText = findViewById(R.id.etLatitude)
        val etLongitude: EditText = findViewById(R.id.etLongitude)
        val etLocationName: EditText = findViewById(R.id.etLocationName)
        val btnSaveLocation: Button = findViewById(R.id.btnSaveLocation)
        val spinnerLocations: Spinner = findViewById(R.id.spinnerLocations)

        // 設定 Spinner Adapter
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, getSpinnerNames())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLocations.adapter = spinnerAdapter

        // 儲存地點按鈕邏輯
        btnSaveLocation.setOnClickListener {
            val latStr = etLatitude.text.toString()
            val lngStr = etLongitude.text.toString()
            val name = etLocationName.text.toString()

            if (latStr.isEmpty() || lngStr.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "請填寫完整資訊 (名稱、緯度、經度)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val lat = latStr.toDouble()
                val lng = lngStr.toDouble()
                saveLocation(name, lat, lng)

                // 更新 Spinner 列表
                spinnerAdapter.clear()
                spinnerAdapter.addAll(getSpinnerNames())
                spinnerAdapter.notifyDataSetChanged()

                // 清空輸入框
                etLocationName.text.clear()

                Toast.makeText(this, "儲存成功", Toast.LENGTH_SHORT).show()

                // 選擇剛存的項目
                spinnerLocations.setSelection(savedLocationsList.size) // The size includes the default "請選擇..." at index 0
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "經緯度格式錯誤", Toast.LENGTH_SHORT).show()
            }
        }

        // 下拉選單選擇邏輯
        spinnerLocations.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val location = savedLocationsList[position - 1] // position 0 is "請選擇..."
                    val latLng = LatLng(location.lat, location.lng)

                    // 更新輸入框
                    etLatitude.setText(location.lat.toString())
                    etLongitude.setText(location.lng.toString())
                    etLocationName.setText(location.name)

                    // 更新地圖標記和視角
                    selectedLatLng = latLng
                    if (::map.isInitialized) {
                        if (selectedMarker == null) {
                            selectedMarker = map.addMarker(MarkerOptions().position(latLng).title(location.name))
                        } else {
                            selectedMarker?.position = latLng
                            selectedMarker?.title = location.name
                        }
                        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


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

            // 同步更新經緯度輸入框
            findViewById<EditText>(R.id.etLatitude).setText(latLng.latitude.toString())
            findViewById<EditText>(R.id.etLongitude).setText(latLng.longitude.toString())
        }
    }

    private fun getSpinnerNames(): List<String> {
        val names = mutableListOf("請選擇儲存的地點...")
        names.addAll(savedLocationsList.map { it.name })
        return names
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

    private fun loadSavedLocations() {
        savedLocationsList.clear()
        val jsonString = sharedPreferences.getString(KEY_LOCATIONS, "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val lat = obj.getDouble("lat")
                val lng = obj.getDouble("lng")
                savedLocationsList.add(SavedLocation(name, lat, lng))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLocation(name: String, lat: Double, lng: Double) {
        savedLocationsList.add(SavedLocation(name, lat, lng))
        saveListToPreferences()
    }

    private fun saveListToPreferences() {
        val jsonArray = JSONArray()
        for (loc in savedLocationsList) {
            val obj = JSONObject()
            obj.put("name", loc.name)
            obj.put("lat", loc.lat)
            obj.put("lng", loc.lng)
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString(KEY_LOCATIONS, jsonArray.toString()).apply()
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
