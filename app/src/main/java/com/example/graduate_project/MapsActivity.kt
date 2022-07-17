package com.example.graduate_project

/** https://firebase.google.com/docs/database/android/read-and-write?hl=zh-cn#kotlin+ktx_1
 *  Firebase 讀資料方法
 *
 *  https://www.youtube.com/watch?v=FotQIcC91V4&ab_channel=CodeStance
 *  Google map 設定目前位置
 *
 *  https://www.raywenderlich.com/230-introduction-to-google-maps-api-for-android-with-kotlin#toc-anchor-006
 *  更新位置
 *
 *  https://stackoverflow.com/questions/67434900/show-multiple-users-location-at-the-same-time-on-a-map-kotlin-firebase-realti?noredirect=1&lq=1
 *  從Firebase拿到多個使用者的位置
 *
 *  https://firebase.google.com/docs/firestore/query-data/get-data
 *  從Firestore拿取資訊
 *  */

import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.graduate_project.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.base.Objects
import com.google.firebase.firestore.*
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var lastLocation: Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var marker: Marker
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false
    private var db = FirebaseFirestore.getInstance()
    var hashMapMarker: HashMap<String, Marker> = HashMap()
    private var registration: ListenerRegistration? = null
    private var clickedMarkerId: String? = null

    companion object {
        private const val LOCATION_REQUEST_CODE = 1
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
    }

    data class Car(
        @get: PropertyName("carId") @set: PropertyName("carId") var carId: String? = "",
        @get: PropertyName("gpsLocation") @set: PropertyName("gpsLocation") var gpsLocation: GeoPoint? = GeoPoint(
            0.0,
            0.0
        ),
        @get: PropertyName("uploadTime") @set: PropertyName("uploadTime") var uploadTime: Date? = null,
        @get: PropertyName("carStatus") @set: PropertyName("carStatus") var carStatus: Int? = null,
        @get: PropertyName("licensePlateNum") @set: PropertyName("licensePlateNum") var licensePlateNum: String? = null,
        @get: PropertyName("HighSusTime") @set: PropertyName("HighSusTime") var HighSusTime: Int? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setOnMarkerClickListener(this)
        setUpMap()
    }

    // 確認是否有權限再設定地圖
    private fun setUpMap() {
        // 沒定位權限：請求權限
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
            return
        }

        // 有定位權限
        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) {
                lastLocation = location
                // 目前定位位置
                val currentLatLng = GeoPoint(location.latitude, location.longitude)
                // 測試用座標
                val ncu = LatLng(24.9714, 121.1945)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ncu, 15f))
            }
        }
        getLocationUpdates()
        startLocationUpdates()
        mMap.setOnMarkerClickListener { marker ->
            val id = marker.title
            clickedMarkerId = id
            Log.d("clickedMarkerId","$clickedMarkerId")
            db.collection("carInfo").document("${id}").get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        var car = document.toObject(MapsActivity.Car::class.java)
                        val dialog = BottomSheetDialog(this)
                        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet, null)
                        val license = view.findViewById<TextView>(R.id.textView)
                        val sus = view.findViewById<TextView>(R.id.textView2)
                        if (car != null) {
                            license.text = "${car.licensePlateNum}"
                            sus.text = "高度疑似酒駕次數為${car.HighSusTime}次"
                            // 進入clickedMarkerId的追蹤畫面
                            snapshot(clickedMarkerId)
                        }
                        dialog.setContentView(view)
                        dialog.show()
                        val submit = view.findViewById<View>(R.id.button2) as Button
                        submit.setOnClickListener {
                            comfirm_dialog()
                        }
                        // 隱藏其他 Marker
                        for (marker in hashMapMarker) {
                            if (marker.key != clickedMarkerId) {
                                marker.value.isVisible = false
                            }
                        }
                        Log.d(TAG, "DocumentSnapshot data: ${document.data}")
                    } else {
                        Log.d(TAG, "No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "get failed with ", exception)
                }
            false
        }
    }

    // 放置 marker
    private fun placeMarkerOnMap(car: Car) {
        var icon = R.drawable.dot_0
        if (car.carStatus == 1) {
            icon = R.drawable.dot_1
        } else if (car.carStatus == 2) {
            icon = R.drawable.dot_2
        }

        //若車輛狀態不為良好
        if (car.carStatus == 1 || car.carStatus == 2) {
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            mMap.addMarker(
                MarkerOptions()
                    .position(lng)
                    .title(car.carId)
                    .icon(BitmapDescriptorFactory.fromResource(icon))
            ).also { marker = it }
            hashMapMarker[car.carId!!] = marker
            Log.d("add_marker", "$hashMapMarker[car.carId]")
        }
    }

    //更動Marker
    private fun markermanagement(car: Car, change_type: DocumentChange) {
        if (change_type.type == DocumentChange.Type.ADDED) {
            placeMarkerOnMap(car)
        }
        if (change_type.type == DocumentChange.Type.MODIFIED) {
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            val marker = hashMapMarker[car.carId]
            Log.d("changing", "${hashMapMarker[car.carId]}")

            //車輛狀態改為良好
            if (car.carStatus == 0) {
//                marker!!.remove()
                hashMapMarker.remove(car.carId)
                marker?.isVisible = false
                Log.d("remove", "${hashMapMarker[car.carId]}")
            }

            //車輛狀態改為疑似酒駕
            if (car.carStatus == 1) {
                Log.d("modify1", "${hashMapMarker[car.carId]}")
                if (marker != null) {
                    marker?.isVisible = true
                    marker?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.dot_1))
                } else {
                    placeMarkerOnMap(car)
                }
            }

            //車輛狀態改為高度疑似酒駕
            if (car.carStatus == 2) {
                Log.d("modify2", "${hashMapMarker[car.carId]}")
                if (marker != null) {
                    marker?.isVisible = true
                    marker?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.dot_2))
                } else {
                    placeMarkerOnMap(car)
                }
            }

            //車輛更改座標
            if (marker?.position != lng) {
                marker?.position = lng
                Log.d("fixed", "${hashMapMarker[car.carId]}")
            }
        }
    }

    override fun onMarkerClick(p0: Marker?) = false

    // 連接資料庫取得位置資訊
    private fun getLocationUpdates() {
        locationRequest = LocationRequest()
        locationRequest.interval = 500
        locationRequest.fastestInterval = 500
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isNotEmpty()) {
                    super.onLocationResult(locationResult)
//                    val location = locationResult.lastLocation

                    // 建立預設地圖(非追蹤)
                    snapshot(null)
                }
            }
        }
//        Log.d("getLocation_bef", "{$registration}")
//        registration = db.collection("carInfo")
//            .addSnapshotListener { value, e ->
//                if (e != null) {
//                    Log.w(TAG, "Listen failed.", e)
//                    return@addSnapshotListener
//                }
//                Log.d("snapshot_value", "{$value}")
//                for (dc in value!!.documentChanges) {
//                    var car = dc.document.toObject(MapsActivity.Car::class.java)
//                    Log.d("try", "$car")
//                    Log.d("snapshot_dc", "{$dc}")
//                    when (dc.type) {
//                        DocumentChange.Type.ADDED -> Log.d(
//                            TAG,
//                            "New marker: ${dc.document.data}"
//                        )
//                        DocumentChange.Type.MODIFIED -> Log.d(
//                            "test2",
//                            "Modified marker: ${dc.document.data}"
//                        )
//                        DocumentChange.Type.REMOVED -> Log.d(
//                            TAG,
//                            "Removed marker: ${dc.document.data}"
//                        )
//                    }
//                    markermanagement(car, dc)
//                }
//            }
//        registration?.remove()
    }

    // 取得最新資訊後開始更新資料
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        // 權限通過
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    public override fun onResume() {
        super.onResume()
        if (!locationUpdateState) {
            getLocationUpdates()
        }
    }

    private fun comfirm_dialog() {
        AlertDialog.Builder(this)
            .setMessage("是否確認退出追蹤模式")
            .setCancelable(false)
            .setPositiveButton("確認", DialogInterface.OnClickListener { dialog, id ->
                Toast.makeText(this, "退出追蹤模式測試", Toast.LENGTH_SHORT).show()
//                hashMapMarker.clear()
//                getLocationUpdates()
                // 退出追蹤模式
                snapshot(null)
                Log.d("getLocationUpdate", "{$registration}")
            })
            .setNegativeButton("取消", DialogInterface.OnClickListener { dialog, id ->
                dialog.cancel()
            })
            .show()
    }

    // 根據是否傳入id判斷建立哪種Listener
    private fun snapshot(id: String?) {
        Log.d("getLocation_bef", "{$registration}")
        if (id == null){
            registration = db.collection("carInfo")
                .addSnapshotListener { value, e ->
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e)
                        return@addSnapshotListener
                    }
                    Log.d("snapshot_value", "{$value}")
                    for (dc in value!!.documentChanges) {
                        var car = dc.document.toObject(MapsActivity.Car::class.java)
                        Log.d("try", "$car")
                        Log.d("snapshot_dc", "{$dc}")
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> Log.d(TAG,"New marker: ${dc.document.data}")
                            DocumentChange.Type.MODIFIED -> Log.d("test2","Modified marker: ${dc.document.data}")
                            DocumentChange.Type.REMOVED -> Log.d(TAG,"Removed marker: ${dc.document.data}")
                        }
                        markermanagement(car, dc)
                    }
                }
        }
        else{
            registration = db.collection("carInfo").whereEqualTo("carId", "$id")
                .addSnapshotListener { document, e ->
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e)
                        return@addSnapshotListener
                    }
                    Log.d("snapshot", "{$document}")
                    for (dc in document!!.documentChanges) {
                        var car = dc.document.toObject(MapsActivity.Car::class.java)
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> Log.d(
                                TAG,
                                "New marker: ${dc.document.data}"
                            )
                            DocumentChange.Type.MODIFIED -> Log.d(
                                "test2",
                                "Modified marker: ${dc.document.data}"
                            )
                            DocumentChange.Type.REMOVED -> Log.d(
                                TAG,
                                "Removed marker: ${dc.document.data}"
                            )
                        }
                        markermanagement(car, dc)
                    }
                }
        }

        Log.d("getLocation_aft", "{$registration}")
    }
}
