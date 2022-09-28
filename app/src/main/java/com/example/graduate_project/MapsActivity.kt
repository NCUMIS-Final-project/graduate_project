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


import MyAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.graduate_project.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.GoogleMap.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.firestore.*
import com.google.gson.Gson
import okhttp3.*
import java.lang.Exception
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var lastLocation: Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var marker: Marker
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false
    private var db = FirebaseFirestore.getInstance()
    private var hashMapMarker: HashMap<String, Marker> = HashMap()
    private var registration: ListenerRegistration? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private var declinePermission: Boolean = false
    private lateinit var icon1: Bitmap
    private lateinit var icon2: Bitmap
    private var recordCount = 0


    // 測試用座標
    private val ncu = LatLng(24.9683, 121.1955)
    //var directionsService = DirectionsService()
    //var directionsDisplay = DirectionsRenderer()

    companion object {
        private const val LOCATION_REQUEST_CODE = 1
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

    data class Record(
        @get: PropertyName("value") @set: PropertyName("value") var value: Float? = null,
        @get: PropertyName("time") @set: PropertyName("time") var time: Date? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //連網設定之一
        if (android.os.Build.VERSION.SDK_INT > 9) {
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
        with(mMap.uiSettings){
            isZoomControlsEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
        }
        mMap.setOnMarkerClickListener(this)
        setUpMap()
    }

    @SuppressLint("MissingPermission")  // 讓編譯器可忽略"沒有要求權限"的報錯
    private fun setUpMap() {

        // 有定位權限，取得使用者位置更新
        if (isLocationPermitted()){
            getLocationUpdates()
            startLocationUpdates()
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                if (location != null) {
                    lastLocation = location

                    // 測試用座標
                    val ncu = LatLng(24.9714, 121.1945)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ncu, 15f))

                    //目前定位位置，實際運作用這個
//                var lastLatLng = location?.let { LatLng(it.latitude,it.longitude) }
//                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, 15f))

                }
            }
        } else {    // 沒權限，請求權限
            askPermission()
        }

        // 建立 marker icon
        icon1 = resizeIcon(R.drawable.map_placeholder_y)
        icon2 = resizeIcon(R.drawable.map_placeholder_r)

        // 建立預設地圖(非追蹤)，不管有沒有權限都可做
        registration?.remove()
        snapshot(null)
        setOnMarkerClickListener()
    }

    // 取得使用者定位位置更新
    private fun getLocationUpdates() {
        locationRequest = LocationRequest()
        locationRequest.interval = 500
        locationRequest.fastestInterval = 500
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isNotEmpty()) {
                    super.onLocationResult(locationResult)
                }
            }
        }.also { locationCallback = it }
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
    @SuppressLint("MissingPermission")      // 讓編譯器可忽略"沒有要求權限"的報錯
    private fun startLocationUpdates() {
        if (isLocationPermitted()){
            // 權限通過
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else{
            askPermission()
        }
    }


    private fun snapshot(id: String?) {
/**     id == null >> 顯示所有marker
        id != null >> 追蹤模式，顯示單一marker   */

        // 先移除所有Marker
        for (marker in hashMapMarker) {
            marker.value.remove()
        }

        // 設定資料庫搜尋條件
        val query = if (id == null) {
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
                val car = dc.document.toObject(Car::class.java)
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

        // 建立酒駕紀錄視窗
        val recordListView = findViewById<View>(R.id.record_list)
        recordListView.visibility = View.GONE

        // 連結畫面元件
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        val license = bottomSheetLayout.findViewById<TextView>(R.id.textView)
        val sus = bottomSheetLayout.findViewById<TextView>(R.id.textView2)
        val submit = bottomSheetLayout.findViewById<View>(R.id.button2) as Button
        val submit2 = bottomSheetLayout.findViewById<View>(R.id.button3) as Button

        mMap.setOnMarkerClickListener { marker ->

            val id = marker.title
            recordList(id)

            val openList =
                bottomSheetLayout.findViewById<View>(R.id.openListBtn) as ImageButton
            // 初始設定為收起狀態
            recordListView.visibility = View.GONE
            openList.setImageResource(R.drawable.up_arrow)

            openList.setOnClickListener {
                if (recordListView.visibility == View.VISIBLE) {
                    recordListView.visibility = View.GONE
                    openList.setImageResource(R.drawable.up_arrow)
                } else {
                    recordListView.visibility = View.VISIBLE
                    openList.setImageResource(R.drawable.down_arrow)
                }
            }

            db.collection("carInfo").document(id).get()
                .addOnSuccessListener { document ->

                    if (document != null) {
                        // 若無定位權限 && 尚未二次拒絕權限 -> 請求權限
                        if (!isLocationPermitted() && !declinePermission) {
                            noLocationToRoute()
                        }

                        val car = document.toObject(Car::class.java)

                        //合成direction api所需ㄉUrl
                        val url = car!!.gpsLocation?.let { getURL(it) }
                        //根據得到的Url繪製路線
                        if (url != null) {
                            drawRoute(url)
                        }

                        // 設定資訊視窗
                        license.text = car.licensePlateNum
                        sus.text = "酒駕次數為${recordCount}次"
                        // 顯示資訊視窗
                        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        } else {
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                        // 進入id的追蹤畫面
                        registration!!.remove()
                        snapshot(id)
                        submit.setOnClickListener {
                            confirmDialog(id, null)
                        }

                        submit2.setOnClickListener {
                            confirmDialog(id, 3)
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

    private fun resizeIcon(id: Int): Bitmap {
        val size = 150
        val resource = BitmapFactory.decodeResource(resources, id)
        return Bitmap.createScaledBitmap(resource, size, size, false)
    }

    // 放置 marker
    private fun placeMarkerOnMap(car: Car) {

        // 依 carStatus 設定 marker icon
        var thisIcon = icon1
        if (car.carStatus == 2){
            thisIcon = icon2
        }

        //若車輛狀態不為良好
        if (car.carStatus == 1 || car.carStatus == 2) {
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            mMap.addMarker(
                MarkerOptions()
                    .position(lng)
                    .title(car.carId)
                    .icon(BitmapDescriptorFactory.fromBitmap(thisIcon))
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
                    marker.isVisible = true
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(icon1))
                } else {
                    placeMarkerOnMap(car)
                }
            }

            //車輛狀態改為高度疑似酒駕
            if (car.carStatus == 2) {
                if (marker != null) {
                    marker.isVisible = true
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(icon2))
                } else {
                    placeMarkerOnMap(car)
                }
            }
            //車輛更改座標
            if (marker?.position != lng) {
                marker?.position = lng
            }
        }
    }

    override fun onMarkerClick(p0: Marker?) = false

    // 要導航時發現沒定位權限ㄉ權限請求訊息
    private fun noLocationToRoute(){
        AlertDialog.Builder(this)
            .setMessage("需取得定位權限才能使用導航服務")
            .setCancelable(false)
            .setPositiveButton("取得導航功能") { _, _ ->
                askPermission()
            }.setNegativeButton("無須導航功能") { dialog, _ ->
                dialog.cancel()
                Toast.makeText(this,"無定位權限，您將在無導航模式使用Alcoholert",Toast.LENGTH_LONG).show()
                declinePermission = true
            }.show()
    }

    private fun confirmDialog(id: String, mode: Int?) {
        // mode == null >> "退出追蹤模式"的訊息
        // mode == 3    >> "狀態改為安全"的訊息
        if (mode == null) {
            AlertDialog.Builder(this)
                .setMessage("確認退出追蹤模式？")
                .setCancelable(false)
                .setPositiveButton("確認") { _, _ ->
                    // 退出追蹤模式
                    registration!!.remove()
                    snapshot(null)
                    mMap.clear()
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.cancel()
                }
                .show()

        } else if (mode == 3) {

            AlertDialog.Builder(this)
                .setMessage("確認將車輛狀態改為安全？")
                .setCancelable(false)
                .setPositiveButton("確認") { _, _ ->
                    // 更改為安全模式
                    val docRef = db.collection("carInfo").document("$id")
                    docRef.update("carStatus", 3)
                    registration!!.remove()
                    snapshot(null)
                    mMap.clear()
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.cancel()
                }
                .show()
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

    private fun getRoute(url: String): Response {
        val client: OkHttpClient = OkHttpClient().newBuilder()
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return client.newCall(request).execute()
    }

    // 多加了「如果沒有使用者位置會回傳null」
    private fun getURL(gpsLocation: GeoPoint): String? {
        val lng = gpsLocation.let { LatLng(it.latitude, it.longitude) }
        return if (isLocationPermitted()){
//            "https://maps.googleapis.com/maps/api/directions/json?origin=${ncu.latitude},${ncu.longitude}&destination=${lng.latitude},${lng.longitude}&key=AIzaSyBe9JNJ-kiMleUTqKnQ8ATEsrp2q0_3pr8"
             return "https://maps.googleapis.com/maps/api/directions/json?origin=${lastLocation.latitude},${lastLocation.longitude}&destination=${lng.latitude},${lng.longitude}&key=AIzaSyBe9JNJ-kiMleUTqKnQ8ATEsrp2q0_3pr8"
        } else null
    }

    private fun drawRoute(url: String) {
        mMap.clear()
        val response = getRoute(url) //根據Url呼叫get_route以得到路徑
        val data = response.peekBody(4194304).string()
        val result = ArrayList<List<LatLng>>()
        try {
            val respObj = Gson().fromJson(data, GoogleMapDTO::class.java)
            val path = ArrayList<LatLng>()

            for (i in 0 until respObj.routes[0].legs[0].steps.size) {
                path.addAll(decodePolyline(respObj.routes[0].legs[0].steps[i].polyline.points))
            }
            result.add(path)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val lineOption = PolylineOptions()

        //路徑的設定和繪製
        for (i in result.indices) {
            lineOption.addAll(result[i])
            lineOption.width(10f)
            lineOption.color(Color.RED)
            lineOption.geodesic(true)
        }
        mMap.addPolyline(lineOption)
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng((lat.toDouble() / 1E5), (lng.toDouble() / 1E5))
            poly.add(latLng)
        }
        return poly
    }

    private fun recordList(id: String) {

        // 建立酒測值列表
        val adapter = MyAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val list = mutableListOf<Record>()

        // 從資料庫拿酒駕紀錄
        db.collection("blowingRecord").whereEqualTo("carId", id).get()
            .addOnSuccessListener { documents ->
                if (documents == null) {
                    list.add(Record(null, null))
                } else {
                    for (document in documents) {
                        list.add(document.toObject(Record::class.java))
                    }

                    // 把紀錄加進recyclerView
                    adapter.updateList(list)
                    recyclerView.layoutManager = LinearLayoutManager(this)
                    recyclerView.swapAdapter(adapter, false)
                }
                recordCount = list.count()
            }
    }

    // 請求權限
    private fun askPermission() {
        if (!isLocationPermitted()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }

    // 根據權限請求結果來決定後續動作
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (isLocationPermitted()){
            // 重新進入MapActivity
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        } else {
            Toast.makeText(this, "無法取得定位位置",Toast.LENGTH_LONG).show()
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ncu, 15f))
        }
    }

    // 回傳是否有定位權限
    private fun isLocationPermitted(): Boolean{
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}