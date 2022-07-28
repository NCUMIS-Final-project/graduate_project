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
import android.graphics.Color
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.example.graduate_project.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.*
import com.google.gson.Gson
import com.google.protobuf.Parser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.lang.Exception
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
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    // 測試用座標
    private val ncu = LatLng(24.9714, 121.1945)
    //var directionsService = DirectionsService()
    //var directionsDisplay = DirectionsRenderer()

    companion object {
        private const val LOCATION_REQUEST_CODE = 1
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
    }

    data class Car(
        @get: PropertyName("carId") @set: PropertyName("carId") var carId: String? = "",
        @get: PropertyName("gpsLocation") @set: PropertyName("gpsLocation") var gpsLocation:
        GeoPoint? = GeoPoint(0.0, 0.0),
        @get: PropertyName("uploadTime") @set: PropertyName("uploadTime") var uploadTime: Date? = null,
        @get: PropertyName("carStatus") @set: PropertyName("carStatus") var carStatus: Int? = null,
        @get: PropertyName("licensePlateNum") @set: PropertyName("licensePlateNum") var licensePlateNum:
        String? = null,
        @get: PropertyName("HighSusTime") @set: PropertyName("HighSusTime") var HighSusTime: Int? = null
    )

    data class Driver(
        @get: PropertyName("name") @set: PropertyName("name") var name: String? = "",
        @get: PropertyName("drunken") @set: PropertyName("drunken") var drunken: Int? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //連網設定之一
        if(android.os.Build.VERSION.SDK_INT>9){
            val policy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
        }

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
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
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

                // 測試用座標
                //val ncu = LatLng(24.9714, 121.1945)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ncu, 15f))

                /**目前定位位置，實際運作用這個
                val currentLatLng = GeoPoint(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                 **/
            }
        }
        // 建立預設地圖(非追蹤)
        registration?.remove()
        snapshot(null)

        // 取得使用者位置、建立地圖
        getLocationUpdates()
        startLocationUpdates()
        setOnMarkerClickListener()
    }

    // 取得使用者定位位置更新
    private fun getLocationUpdates() {
        locationRequest = LocationRequest()
        locationRequest.interval = 500
        locationRequest.fastestInterval = 500
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isNotEmpty()) {
                    super.onLocationResult(locationResult)
                }
            }
        }
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


    private fun snapshot(id: String?) {
    // id == null >> 顯示所有marker
    // id != null >> 追蹤模式，顯示單一marker

        // 先移除所有Marker
        for (marker in hashMapMarker) {
            marker.value.remove()
        }

        // 設定資料庫搜尋條件
        var query = if (id == null) {
            db.collection("carInfo").whereNotEqualTo("carId", null)
        } else {
            db.collection("carInfo").whereEqualTo("carId", "$id")
        }

        // 建立Listener
        registration = query.addSnapshotListener { value, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }
            for (dc in value!!.documentChanges) {
                var car = dc.document.toObject(MapsActivity.Car::class.java)
                when (dc.type) {
                    DocumentChange.Type.ADDED -> Log.d(TAG, "New marker: ${dc.document.data}")
                    DocumentChange.Type.REMOVED -> Log.d(TAG, "Removed marker: ${dc.document.data}")
                    DocumentChange.Type.MODIFIED -> {
                        // 如果是追蹤模式，座標改變時更改camera位置
                        if (id != null) {
                            mMap.animateCamera(
                                CameraUpdateFactory.newLatLng(
                                    car.gpsLocation?.let { LatLng(it.latitude, it.longitude) })
                            )
                        }
                    }
                }
                // marker顯示設定
                markerManagement(car, dc)
            }
        }
    }

    private fun setOnMarkerClickListener() {
        // 建立資訊視窗
        val bottomSheetLayout = findViewById<ConstraintLayout>(R.id.layoutBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        mMap.clear()
        mMap.setOnMarkerClickListener { marker ->
            val id = marker.title
            clickedMarkerId = id

            db.collection("carInfo").document("${id}").get()
                .addOnSuccessListener { document ->

                    if (document != null) {
                        var car = document.toObject(MapsActivity.Car::class.java)
                        //合成direction api所需ㄉUrl
                        val url= car!!.gpsLocation?.let { getURL(it) }
                        //根據得到的Url繪製路線
                        if (url != null) {
                            draw_route(url)
                        }
                        db.collection("driver").document("${id}").get()
                            .addOnSuccessListener { document ->

                                if (document != null) {
                                    var driver = document.toObject(MapsActivity.Driver::class.java)
                                    val license = bottomSheetLayout.findViewById<TextView>(R.id.textView)
                                    val sus = bottomSheetLayout.findViewById<TextView>(R.id.textView2)

                                    if (car != null) {
                                        // 設定資訊視窗內容
                                        license.text = car.licensePlateNum
                                        sus.text = "酒駕次數為${driver!!.drunken}次"
                                        // 顯示資訊視窗
                                        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_EXPANDED) {
                                            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                                        } else {
                                            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                                        }
                                        // 進入clickedMarkerId的追蹤畫面
                                        registration!!.remove()
                                        snapshot(clickedMarkerId)
                                    }
                                    val submit = bottomSheetLayout.findViewById<View>(R.id.button2) as Button
                                    submit.setOnClickListener {
                                        comfirmDialog(null)
                                    }
                                    val submit2 = bottomSheetLayout.findViewById<View>(R.id.button3) as Button
                                    submit2.setOnClickListener {
                                        val docRef = db.collection("carInfo").document("$id")
                                        docRef.update("carStatus", 3)
                                        comfirmDialog(3)
                                    }
                                } else {
                                    Log.d(TAG, "No such document")
                                }
                            }.addOnFailureListener { exception ->
                                Log.d(TAG, "get failed with ", exception)
                            }
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
        }
    }

    //更動Marker
    private fun markerManagement(car: Car, change_type: DocumentChange) {
        if (change_type.type == DocumentChange.Type.ADDED) {
            placeMarkerOnMap(car)
        }
        if (change_type.type == DocumentChange.Type.MODIFIED) {
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            val marker = hashMapMarker[car.carId]
            Log.d("changing", "${hashMapMarker[car.carId]}")

            //車輛狀態改為良好
            if (car.carStatus == 0) {
                hashMapMarker.remove(car.carId)
                marker?.isVisible = false
            }

            //車輛狀態改為疑似酒駕
            if (car.carStatus == 1) {
                if (marker != null) {
                    marker?.isVisible = true
                    marker?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.dot_1))
                } else {
                    placeMarkerOnMap(car)
                }
            }

            //車輛狀態改為高度疑似酒駕
            if (car.carStatus == 2) {
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

    private fun comfirmDialog(mode : Int?) {
        // mode == null >> "退出追蹤模式"的訊息
        // mode == 3    >> "狀態改為安全"的訊息
        var message = ""
        if (mode == null){
            message = "確認退出追蹤模式？"
        } else if (mode == 3){
            message = "確認將車輛狀態改為安全？"
        }
        AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("確認", DialogInterface.OnClickListener { dialog, id ->
                // 退出追蹤模式
                registration!!.remove()
                snapshot(null)
                mMap.clear()
                if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            })
            .setNegativeButton("取消", DialogInterface.OnClickListener { dialog, id ->
                dialog.cancel()
            })
            .show()
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
    fun get_route(url:String):Response {
        val client: OkHttpClient = OkHttpClient().newBuilder()
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val response: Response = client.newCall(request).execute()
        return response
    }

    fun getURL(gpsLocation: GeoPoint):String{
        val lng = gpsLocation.let { LatLng(it.latitude, it.longitude) }
//        return "https://maps.googleapis.com/maps/api/directions/json?origin=${lastLocation.latitude},${lastLocation.longitude}&destination=${lng.latitude},${lng.longitude}&key=AIzaSyBe9JNJ-kiMleUTqKnQ8ATEsrp2q0_3pr8"
        return "https://maps.googleapis.com/maps/api/directions/json?origin=${ncu.latitude},${ncu.longitude}&destination=${lng.latitude},${lng.longitude}&key=AIzaSyBe9JNJ-kiMleUTqKnQ8ATEsrp2q0_3pr8"
    }

    fun draw_route(url:String){
        val response = get_route(url) //根據Url呼叫get_route以得到路徑
        val data = response.peekBody(4194304)!!.string()
        val result = ArrayList<List<LatLng>>()
        try{
            val respObj = Gson().fromJson(data,GoogleMapDTO::class.java)
            val path = ArrayList<LatLng>()

            for (i in 0..(respObj.routes[0].legs[0].steps.size-1)){
                val startLatLng = LatLng(respObj.routes[0].legs[0].steps[i].start_location.lat.toDouble(),
                    respObj.routes[0].legs[0].steps[i].start_location.lng.toDouble())
                path.add(startLatLng)
                val endLatLng = LatLng(respObj.routes[0].legs[0].steps[i].end_location.lat.toDouble(),
                    respObj.routes[0].legs[0].steps[i].end_location.lng.toDouble())
                path.add(endLatLng)
            }
            result.add(path)
        }catch (e:Exception){
            e.printStackTrace()
        }
        val lineoption = PolylineOptions()

        //路徑的設定和繪製
        for (i in result.indices){
            lineoption.addAll(result[i])
            lineoption.width(10f)
            lineoption.color(Color.BLUE)
            lineoption.geodesic(true)
        }
        mMap.addPolyline(lineoption)
    }
}



