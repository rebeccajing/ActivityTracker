package biz.bokhorst.am;

/*
 Copyright 2014 Marcel Bokhorst
 All Rights Reserved

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

public class BackgroundService extends IntentService implements
		ConnectionCallbacks, OnConnectionFailedListener, SensorEventListener {
	private static String TAG = "AM";

	private static ActivityRecognitionClient activityRecognitionClient = null;
	private static PendingIntent locationPendingIntent = null;
	private static boolean stepCounterRegistered = false;

	public static final String ACTION_START = "Start";
	public static final String ACTION_LOCATION = "Location";
	public static final String ACTION_STEPS = "Steps";

	public BackgroundService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.w(TAG, "Handling intent, action=" + intent.getAction());

		// Start activity recognition
		ensureActivityRecognition();

		// Start location updates
		ensureLocationUpdates();

		// Start step counter
		ensureStepCounting();

		// Check for activity recognition result
		if (ActivityRecognitionResult.hasResult(intent))
			handleActivityRecognition(intent);
		else if (ACTION_START.equals(intent.getAction()))
			handleStart(intent);
		else if (ACTION_LOCATION.equals(intent.getAction()))
			handleLocationChanged(intent);
		else if (ACTION_STEPS.equals(intent.getAction()))
			handleStepsChanged(intent);
	}

	// Activity recognition setup

	private void ensureActivityRecognition() {
		// Check for Play services
		int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (result == ConnectionResult.SUCCESS) {
			// Create activity recognition client
			if (activityRecognitionClient == null)
				activityRecognitionClient = new ActivityRecognitionClient(this,
						this, this);

			// Connect to activity recognition
			if (!activityRecognitionClient.isConnected()
					&& !activityRecognitionClient.isConnecting()) {
				Log.w(TAG, "Connecting to activity recognition client");
				activityRecognitionClient.connect();
			}
		} else
			Log.w(TAG, "Play services not available, result=" + result);
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		Log.e(TAG,
				"Connection to Play services failed, result="
						+ result.getErrorCode());
	}

	@Override
	public void onConnected(Bundle hint) {
		Log.w(TAG, "Requesting activity updates");
		int interval = 60 * 1000; // TODO: setting
		PendingIntent activityCallbackIntent = PendingIntent.getService(this,
				0, new Intent(this, BackgroundService.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		activityRecognitionClient.requestActivityUpdates(interval,
				activityCallbackIntent);
	}

	@Override
	public void onDisconnected() {
		Log.w(TAG, "Disconnected from Play services");
	}

	// Location updates setup

	private void ensureLocationUpdates() {
		if (locationPendingIntent == null) {
			// Build pending intent
			Intent locationIntent = new Intent(this, BackgroundService.class);
			locationIntent.setAction(BackgroundService.ACTION_LOCATION);

			locationPendingIntent = PendingIntent.getService(this, 0,
					locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			// Request location updates
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locationManager.removeUpdates(locationPendingIntent);
			int minTime = 60 * 1000; // TODO: setting
			int minDistance = 50; // TODO: setting
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			locationManager.requestLocationUpdates(minTime, minDistance,
					criteria, locationPendingIntent);
			Log.w(TAG, "Requested location updates");
		}
	}

	// Step counter setup

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void ensureStepCounting() {
		if (getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
			if (!stepCounterRegistered) {
				SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
				Sensor countSensor = sensorManager
						.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
				sensorManager.registerListener(this, countSensor,
						SensorManager.SENSOR_DELAY_NORMAL);
				stepCounterRegistered = true;
				Log.w(TAG, "Step counter listener registered");
			}
		} else
			Log.w(TAG, "No hardware step counter");
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		Log.w(TAG, "Accuracy changed, accuracy=" + accuracy);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		int steps = (int) event.values[0];
		Log.w(TAG, "Sensor changed, steps=" + steps);
		Intent intentSteps = new Intent(this, BackgroundService.class);
		intentSteps.setAction(ACTION_STEPS);
		intentSteps.putExtra(ACTION_STEPS, steps);
		startService(intentSteps);
	}

	// Logic

	private void handleStart(Intent intent) {
	}

	private void handleActivityRecognition(Intent intent) {
		// Register activity
		ActivityRecognitionResult result = ActivityRecognitionResult
				.extractResult(intent);
		DetectedActivity mostProbableActivity = result
				.getMostProbableActivity();
		new DatabaseHelper(this).registerActivity(result.getTime(),
				mostProbableActivity.getType(),
				mostProbableActivity.getConfidence());

		SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss",
				Locale.getDefault());
		for (DetectedActivity activity : result.getProbableActivities())
			Log.w(TAG, TIME_FORMATTER.format(new Date(result.getTime()))
					+ " Activity " + getNameFromType(activity.getType()) + " "
					+ activity.getConfidence() + " %");
	}

	private void handleLocationChanged(Intent intent) {
		Location location = (Location) intent.getExtras().get(
				LocationManager.KEY_LOCATION_CHANGED);
		Log.w(TAG, "Location=" + location);
		new DatabaseHelper(this).registerDetail(new Date().getTime(),
				DatabaseHelper.TYPE_LOCATION, location.toString());
	}

	private void handleStepsChanged(Intent intent) {
		int steps = intent.getIntExtra(ACTION_STEPS, -1);
		Log.w(TAG, "Steps=" + steps);
		new DatabaseHelper(this).registerDetail(new Date().getTime(),
				DatabaseHelper.TYPE_LOCATION, Integer.toString(steps));
	}

	// Helper methods

	private String getNameFromType(int activityType) {
		switch (activityType) {
		case DetectedActivity.IN_VEHICLE:
			return getString(R.string.activity_in_vehicle);
		case DetectedActivity.ON_BICYCLE:
			return getString(R.string.activity_on_bicycle);
		case DetectedActivity.ON_FOOT:
			return getString(R.string.activity_on_foot);
		case DetectedActivity.STILL:
			return getString(R.string.activity_still);
		case DetectedActivity.UNKNOWN:
			return getString(R.string.activity_unknown);
		case DetectedActivity.TILTING:
			return getString(R.string.activity_tilting);
		}
		return getString(R.string.activity_unknown);
	}
}
