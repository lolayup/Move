package com.khaled.move

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.ui.MapScreen
import com.khaled.move.ui.theme.MoveTheme

private const val TAG = "MoveMainActivity"

class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d(TAG, "Permissions callback: granted=$granted")
        viewModel.setLocationPermissionGranted(granted)
        if (granted) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION) // Fallback for older devices

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val locationState by viewModel.location.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                checkPermissions()
            }

            LaunchedEffect(locationState.isLocationTracking) {
                if (locationState.isLocationTracking) {
                    startLocationUpdates()
                } else {
                    stopLocationUpdates()
                }
            }

            MoveTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MapScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            val azimuth = Math.toDegrees(orientationValues[0].toDouble())
            viewModel.onHeadingUpdate(azimuth)
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            viewModel.onHeadingUpdate(event.values[0].toDouble())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        Log.d(TAG, "Checking permissions: fine=$fineLocation, coarse=$coarseLocation")

        if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
            viewModel.setLocationPermissionGranted(true)
            startLocationUpdates()
        } else {
            Log.d(TAG, "Requesting permissions...")
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private val playLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            Log.d(TAG, "Play Services location result: ${locationResult.locations.size} points")
            for (location in locationResult.locations) {
                dispatchLocation(location)
            }
        }
    }

    private val legacyLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "Legacy LocationManager result: ${location.latitude}, ${location.longitude}")
            dispatchLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun dispatchLocation(location: Location) {
        viewModel.onLocationUpdate(
            NavigationLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = location.time,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                bearingDegrees = if (location.hasBearing()) location.bearing else null,
                speedMetersPerSecond = if (location.hasSpeed()) location.speed else null
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start updates: Permission denied")
            return
        }

        if (isGooglePlayServicesAvailable()) {
            Log.d(TAG, "Starting Play Services location updates")
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                playLocationCallback,
                Looper.getMainLooper()
            )
        } else {
            Log.d(TAG, "Play Services unavailable, falling back to LocationManager")
            val providers = locationManager?.getProviders(true) ?: emptyList()
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    legacyLocationListener,
                    Looper.getMainLooper()
                )
            }
            if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    legacyLocationListener,
                    Looper.getMainLooper()
                )
            }
        }
    }

    private fun stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates")
        fusedLocationClient.removeLocationUpdates(playLocationCallback)
        locationManager?.removeUpdates(legacyLocationListener)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(this)
        return result == ConnectionResult.SUCCESS
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.location.value.isLocationTracking) {
            startLocationUpdates()
        }
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep updates running if we are tracking/navigating
        if (!viewModel.location.value.isLocationTracking && !viewModel.navigationState.value.status.isNavigating()) {
            stopLocationUpdates()
        }
        sensorManager?.unregisterListener(this)
    }

    private fun NavigationStatus.isNavigating(): Boolean {
        return this == NavigationStatus.Navigating || 
               this == NavigationStatus.Stationary || 
               this == NavigationStatus.Rerouting
    }
}
