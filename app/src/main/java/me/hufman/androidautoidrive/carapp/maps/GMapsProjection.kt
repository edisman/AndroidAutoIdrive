package me.hufman.androidautoidrive.carapp.maps

import android.Manifest
import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.ImageView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import kotlinx.android.synthetic.main.gmaps_projection.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.TimeUtils

const val INTENT_GMAP_RELOAD_SETTINGS = "me.hufman.androidautoidrive.carapp.gmail.RELOAD_SETTINGS"

class GMapsProjection(val parentContext: Context, display: Display): Presentation(parentContext, display) {
	val TAG = "GMapsProjection"
	var map: GoogleMap? = null
	var view: ImageView? = null
	val settingsListener = SettingsReload()
	val locationProvider = LocationServices.getFusedLocationProviderClient(context)!!
	val locationCallback = LocationCallbackImpl()
	var lastLocation: LatLng? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		window.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
		setContentView(R.layout.gmaps_projection)

		gmapView.onCreate(savedInstanceState)
		gmapView.getMapAsync {
			map = it

			// load initial theme settings for the map
			applySettings()

			it.isIndoorEnabled = false
			it.isTrafficEnabled = true

			if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				it.isMyLocationEnabled = true
			}

			with (it.uiSettings) {
				isCompassEnabled = true
				isMyLocationButtonEnabled = false
			}

			locationProvider.lastLocation.addOnCompleteListener { location ->
				if (location.isSuccessful && location.result != null) {
					val result = location.result ?: return@addOnCompleteListener
					lastLocation = LatLng(result.latitude, result.longitude)
					it.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLocation, 10f))
					it.animateCamera(CameraUpdateFactory.zoomTo(15f))

					// try to apply settings again, for auto day/night mode with location
					applySettings()
				}
			}
		}

		// watch for map settings
		context.registerReceiver(settingsListener, IntentFilter(INTENT_GMAP_RELOAD_SETTINGS))
	}

	override fun onStart() {
		super.onStart()
		Log.i(TAG, "Projection Start")
		gmapView.onStart()
		gmapView.onResume()

		if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			val locationRequest = LocationRequest()
			locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
			locationRequest.interval = 10000
			locationRequest.fastestInterval = 100

			locationProvider.requestLocationUpdates(locationRequest, locationCallback, null)
		}
	}

	fun applySettings() {
		val style = AppSettings[AppSettings.KEYS.GMAPS_STYLE].toLowerCase()
		Log.i(TAG, "Setting gmap style to $style")

		val location = lastLocation
		val mapstyleId = when(style) {
			"auto" -> if (location == null || TimeUtils.getDayMode(LatLong(location.latitude, location.longitude))) null else R.raw.gmaps_style_night
			"night" -> R.raw.gmaps_style_night
			"aubergine" -> R.raw.gmaps_style_aubergine
			"midnight_commander" -> R.raw.gmaps_style_midnight_commander
			else -> null
		}
		val mapstyle = if (mapstyleId != null) MapStyleOptions.loadRawResourceStyle(parentContext, mapstyleId) else null
		map?.setMapStyle(mapstyle)
	}

	override fun onStop() {
		super.onStop()
		Log.i(TAG, "Projection Stopped")
		gmapView.onPause()
		gmapView.onStop()
//		gmapView.onDestroy()
		locationProvider.removeLocationUpdates(locationCallback)
		context.unregisterReceiver(settingsListener)
	}

	override fun onSaveInstanceState(): Bundle {
		val output = super.onSaveInstanceState()
		gmapView.onSaveInstanceState(output)
		return output
	}

	inner class LocationCallbackImpl: LocationCallback() {
		override fun onLocationResult(location: LocationResult?) {
			if (location != null && location.lastLocation != null) {
				lastLocation = LatLng(location.lastLocation.latitude, location.lastLocation.longitude)
				map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(location.lastLocation.latitude, location.lastLocation.longitude)))
			}
		}
	}

	inner class SettingsReload: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == INTENT_GMAP_RELOAD_SETTINGS) {
				// reload any settings that were changed in the UI
				applySettings()
			}
		}
	}
}