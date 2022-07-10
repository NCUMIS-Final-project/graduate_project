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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.graduate_project.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
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
        @get: PropertyName("licensePlateNum") @set: PropertyName("licensePlateNum") var licensePlateNum: String?= null
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

    }

    // 放置 marker
    private fun placeMarkerOnMap(car: Car) {
        if (car.carStatus!=0) { //若車輛狀態不為良好
            val lng = car.gpsLocation?.let { LatLng(it.latitude, it.longitude) }
            marker = mMap.addMarker(
                MarkerOptions()
                    .position(lng)
                    .title(car.carId)
//                    .visible(car.carStatus!=0)
            )
            hashMapMarker[car.carId!!] = marker
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
                //這裡要放更改顏色的fun
            }
            //車輛狀態改為高度疑似酒駕
            if(car.carStatus==2){
                Log.d("modify2","${hashMapMarker[car.carId]}")
                //這裡要放更改顏色的fun
            }
            //車輛更改座標
            if (marker?.position!=lng) {
                marker?.position = lng
                Log.d("fixed","${hashMapMarker[car.carId]}")
                //placeMarkerOnMap(car)
            }
        }
    }

    //移除Marker
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
                                    DocumentChange.Type.ADDED -> Log.d(TAG, "New city: ${dc.document.data}")
                                    DocumentChange.Type.MODIFIED -> Log.d("test2", "Modified city: ${dc.document.data}")
                                    DocumentChange.Type.REMOVED -> Log.d(TAG, "Removed city: ${dc.document.data}")
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

/*    private fun createLocationRequest() {
     // 初始化locationRequest，刪掉會報錯
     locationRequest = LocationRequest()

     locationRequest.interval = 500
     locationRequest.fastestInterval = 500
     locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

     val builder = LocationSettingsRequest.Builder()
         .addLocationRequest(locationRequest)

     //val client = LocationServices.getSettingsClient(this)
//        val task = client.checkLocationSettings(builder.build())


     locationCallback = object : LocationCallback() {
         override fun onLocationResult(locationResult: LocationResult) {
             if (locationResult.locations.isNotEmpty()) {
                 val location = locationResult.lastLocation

                 lateinit var databaseRef: DatabaseReference
                 databaseRef = Firebase.database.reference
                 val locationlogging = LocationLogging(location.latitude, location.longitude)
                 databaseRef.child("/users/userlocation").setValue(locationlogging)

                     .addOnSuccessListener {
                         Toast.makeText(applicationContext, "Locations written into the database", Toast.LENGTH_LONG).show()
                     }
                     .addOnFailureListener {
                         Toast.makeText(applicationContext, "Error occured while writing the locations", Toast.LENGTH_LONG).show()
                     }


                 if (location != null) {
                     val latLng = LatLng(location.latitude, location.longitude)
                     val markerOptions = MarkerOptions().position(latLng).title("$latLng")
                     mMap.addMarker(markerOptions)
                     mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                 }
             }
         }
     }


     task.addOnSuccessListener {
         locationUpdateState = true
         startLocationUpdates()
     }
     task.addOnFailureListener { e ->
         if (e is ResolvableApiException) {
             // Location settings are not satisfied, but this can be fixed
             // by showing the user a dialog.
             try {
                 // Show the dialog by calling startResolutionForResult(),
                 // and check the result in onActivityResult().
                 e.startResolutionForResult(this@MapsActivity,
                     REQUEST_CHECK_SETTINGS)
             } catch (sendEx: IntentSender.SendIntentException) {
                 // Ignore the error.
             }
         }
     }
 }   */


/*
原本在onCreat()裡
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                if (locationResult.locations.isNotEmpty()) {

                    lateinit var databaseRef: DatabaseReference
                    databaseRef = Firebase.database.reference
                    val locationlogging = LocationLogging(location.latitude, location.longitude)
                    databaseRef.child("/users/userlocation").setValue(locationlogging)

                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "Locations written into the database", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(applicationContext, "Error occured while writing the locations", Toast.LENGTH_LONG).show()
                        }
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                val db =  FirebaseFirestore.getInstance()
                Log.d(TAG, "test")
                    val carLocRef = rootRef.collection("carLocation")
                    val GPSRef = carLocRef.document(carLoc)
                val docRef = db.collection("carLocation").document("carLoc")
                docRef.get()
                    .addOnSuccessListener { task ->
                        if (task != null) {
                            Log.d(TAG, "DocumentSnapshot data: ${task.data}")
                                val latitude = task.data.getLatitude("latitude")
                                val longitude = document.getDouble("longitude")
                                Log.d(TAG, latitude + ", " + longitude)
                        } else {
                            Log.d(TAG, "No such document")
                        }
                    }
                    .addOnFailureListener { exception ->
                            Log.d(TAG, "get failed with ", exception)
                    }

                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        val markerOptions = MarkerOptions().position(latLng)
                        mMap.addMarker(markerOptions)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }
                lastLocation = p0.lastLocation!!
                placeMarkerOnMap(lastLocation)
            }
        }
        createLocationRequest()*/
