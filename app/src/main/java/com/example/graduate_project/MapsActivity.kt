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
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
//    private var registration: ListenerRegistration? = null
    private var clickedMarkerId: String ?= null

    companion object{
        private const val LOCATION_REQUEST_CODE = 1
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
    }

    data class Car(
        @get: PropertyName("carId") @set: PropertyName("carId") var carId: String? = "",
        @get: PropertyName("gpsLocation") @set: PropertyName("gpsLocation") var gpsLocation: GeoPoint? = GeoPoint(0.0,0.0),
        @get: PropertyName("uploadTime") @set: PropertyName("uploadTime") var uploadTime: Date?= null,
        @get: PropertyName("carStatus") @set: PropertyName("carStatus") var carStatus: Int?= null,
        @get: PropertyName("licensePlateNum") @set: PropertyName("licensePlateNum") var licensePlateNum: String?= null,
        @get: PropertyName("HighSusTime") @set: PropertyName("HighSusTime") var HighSusTime: Int?= null
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),LOCATION_REQUEST_CODE)
            return
        }

        // 有定位權限
        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener(this) {location ->
            if (location != null) {
                lastLocation = location
                val currentLatLng = GeoPoint(location.latitude, location.longitude)
//                val returnLocation = Car("yourPosition", currentLatLng, null)
//                db.collection("carLocation").document("yourPosition").set(returnLocation, SetOptions.merge())
                val ncu = LatLng(24.9714,121.1945)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ncu, 15f))
            }
        }
        getLocationUpdates()
        startLocationUpdates()
        mMap.setOnMarkerClickListener { marker -> // on marker click we are getting the title of our marker
            // which is clicked and displaying it in a toast message.
            val id=marker.title
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
                            sus.text="高度疑似酒駕次數為${car.HighSusTime}次"
                            // 紀錄目前點擊 marker 的 id(title)
                            clickedMarkerId = id
                        }
                        dialog.setContentView(view)
                        dialog.show()
                        Log.d(TAG, "DocumentSnapshot data: ${document.data}")
                    } else {
                        Log.d(TAG, "No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "get failed with ", exception)
                }
            // 追蹤marker的位置
//            MarkerClickTracking(id)
//            registration.remove()
//            false
            false
        }
    }

    // 追蹤已點擊marker的位置
//    private fun MarkerClickTracking(id: String){
//        registration?.remove()
//        registration = db.collection("carInfo").document("${id}")
//            .addSnapshotListener { document, e ->
//                if (e != null) {
//                    Log.w(TAG, "Listen failed.", e)
//                    return@addSnapshotListener
//                }
//                if (document != null && document.exists()) {
//                    var car = document.toObject(MapsActivity.Car::class.java)
//                    Log.d("click_car", "$car")
//                    Log.d("click_document", "$document")
//                    var updateGps = car?.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
//                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(updateGps, 15f))
////                    mMap.animateCamera(CameraUpdateFactory.newLatLng(updateGps))
//                }
//            }
//    }


    // 放置 marker
    private fun placeMarkerOnMap(car: Car) {
        var icon = R.drawable.dot_0
        if (car.carStatus == 1){
            icon = R.drawable.dot_1
        } else if(car.carStatus == 2){
            icon = R.drawable.dot_2
        }
        if (car.carStatus!=0) { //若車輛狀態不為良好
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            mMap.addMarker(
                MarkerOptions()
                    .position(lng)
                    .title(car.carId)
                    .icon(BitmapDescriptorFactory.fromResource(icon))
            ).also { marker = it }
            hashMapMarker[car.carId!!] = marker
//            marker?.showInfoWindow()
            Log.d("add_marker","$hashMapMarker[car.carId]")
        }
    }

    //更動Marker
    private fun markermanagement(car:Car,change_type:DocumentChange){
        if(change_type.type==DocumentChange.Type.ADDED){
            placeMarkerOnMap(car)
        }
        if(change_type.type==DocumentChange.Type.MODIFIED){
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            val marker = hashMapMarker[car.carId]
            Log.d("changing","${hashMapMarker[car.carId]}")
            //車輛狀態改為良好
            if(car.carStatus==0){
                marker!!.remove()
                hashMapMarker.remove(car.carId)
                Log.d("remove","${hashMapMarker[car.carId]}")
            }
            //車輛狀態改為疑似酒駕
            if(car.carStatus==1){
                Log.d("modify1","${hashMapMarker[car.carId]}")
                if(marker!=null){
                    marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.dot_1))
                } else{
                    placeMarkerOnMap(car)
                }

                //這裡要放更改顏色的fun
            }
            //車輛狀態改為高度疑似酒駕
            if(car.carStatus==2){
                Log.d("modify2","${hashMapMarker[car.carId]}")
                if(marker!=null) {
                    marker?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.dot_2))
                }else{
                    placeMarkerOnMap(car)
                }
                //這裡要放更改顏色的fun
            }
            //車輛更改座標
            if (marker?.position!=lng) {
                marker?.position = lng
                Log.d("fixed","${hashMapMarker[car.carId]}")
                //placeMarkerOnMap(car)
            }
            if(car.carId==clickedMarkerId){
                Log.d("click_car", "$car")
                var updateGps = car?.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(updateGps, 15f))
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

                    /**                    // 讀取集合裡所有資料(文件)
                    val docRef = db.collection("carLocation")
                    db.collection("carLocation")
                    .get()
                    .addOnSuccessListener { documents ->
                    for(document in documents){
                    if (document.exists()) {
                    var car = document.toObject(Car::class.java)!!
                    if (car != null) {
                    placeMarkerOnMap(car)
                    }
                    }else{
                    Toast.makeText(this@MapsActivity, "Error!", Toast.LENGTH_SHORT).show()
                    }
                    }
                    }.addOnFailureListener { exception ->
                    Log.d(TAG, "get failed with ", exception)
                    }*/

                    db.collection("carInfo")
                        .addSnapshotListener { value, e ->
                            if (e != null) {
                                Log.w(TAG, "Listen failed.", e)
                                return@addSnapshotListener
                            }
                            for (dc in value!!.documentChanges) {
                                var car = dc.document.toObject(MapsActivity.Car::class.java)
                                Log.d("try","$car")
                                when (dc.type) {
                                    DocumentChange.Type.ADDED -> Log.d(TAG, "New marker: ${dc.document.data}")
                                    DocumentChange.Type.MODIFIED -> Log.d("test2", "Modified marker: ${dc.document.data}")
                                    DocumentChange.Type.REMOVED -> Log.d(TAG, "Removed marker: ${dc.document.data}")
                                }
                                markermanagement(car,dc)

                            }
                        }
                }
            }
        }
    }

    // 取得最新資訊後開始更新資料
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        // 權限通過
        fusedLocationClient.requestLocationUpdates(locationRequest,locationCallback, null)
    }

//  目前沒有用到
//    private fun stopLocationUpdates(){
//        fusedLocationClient.removeLocationUpdates(locationCallback)
//    }

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
}