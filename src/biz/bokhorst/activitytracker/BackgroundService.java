package biz.bokhorst.activitytracker;

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
import android.content.SharedPreferences;
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

import biz.bokhorst.activitytracker.DatabaseHelper.ActivityData;
import biz.bokhorst.activitytracker.DatabaseHelper.ActivityRecord;

public class BackgroundService extends IntentService implements
		ConnectionCallbacks, OnConnectionFailedListener, SensorEventListener {
	private static String TAG = "ATRACKER";

	private static ActivityRecognitionClient activityRecognitionClient = null;
	private static PendingIntent locationPendingIntent = null;
	private static boolean stepCounterRegistered = false;

	public static final String ACTION_INIT = "Initialize";
	public static final String ACTION_LOCATION = "Location";
	public static final String ACTION_STEPS = "Steps";

	public BackgroundService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.w(TAG, "Handling intent, action=" + intent.getAction());

		// Start services
		ensureActivityRecognition();
		ensureLocationUpdates();
		ensureStepCounting();

		// Handle intent
		if (ActivityRecognitionResult.hasResult(intent))
			handleActivityRecognition(intent);
		else if (ACTION_INIT.equals(intent.getAction()))
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
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
			int steps = (int) event.values[0];
			Log.w(TAG, "Sensor changed, steps=" + steps);
			Intent intentSteps = new Intent(this, BackgroundService.class);
			intentSteps.setAction(ACTION_STEPS);
			intentSteps.putExtra(ACTION_STEPS, steps);
			startService(intentSteps);
		}
	}

	// Logic

	private void handleStart(Intent intent) {
	}

	private void handleActivityRecognition(Intent intent) {
		DatabaseHelper dh = new DatabaseHelper(this);

		// Register activity
		ActivityRecognitionResult result = ActivityRecognitionResult
				.extractResult(intent);
		DetectedActivity mostProbableActivity = result
				.getMostProbableActivity();

		boolean newActivity = false;
		if (mostProbableActivity.getType() != DetectedActivity.TILTING)
			newActivity = dh.registerActivityRecord(new ActivityRecord(result
					.getTime(), mostProbableActivity.getType()));
		for (DetectedActivity activity : result.getProbableActivities())
			dh.registerActivityData(new ActivityData(result.getTime(), activity
					.getType(), activity.getConfidence()));

		// Request location update
		if (newActivity) {
			// Build pending intent
			Intent locationIntent = new Intent(this, BackgroundService.class);
			locationIntent.setAction(BackgroundService.ACTION_LOCATION);

			PendingIntent locationPendingIntent = PendingIntent.getService(
					this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			// Request single location update
			int locationAccuracy = Criteria.ACCURACY_FINE; // TODO: setting
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			Criteria criteria = new Criteria();
			criteria.setAccuracy(locationAccuracy);
			locationManager
					.requestSingleUpdate(criteria, locationPendingIntent);
			Log.w(TAG, "Requested single location update");
		}
	}

	private void handleLocationChanged(Intent intent) {
		Location location = (Location) intent.getExtras().get(
				LocationManager.KEY_LOCATION_CHANGED);
		Log.w(TAG, "Location=" + location);

		new DatabaseHelper(this).registerActivityData(new ActivityData(
				ActivityData.TYPE_TRACKPOINT, location));
	}

	private void handleStepsChanged(Intent intent) {
		// TODO: shared preference
		SharedPreferences prefs = getSharedPreferences("activity",
				Context.MODE_MULTI_PROCESS);

		int steps = intent.getIntExtra(ACTION_STEPS, -1);
		int last = prefs.getInt("Steps", 0);
		int delta = steps - last;
		Log.w(TAG, "Steps=" + steps + " Delta=" + delta);

		int minDelta = 10; // TODO: setting
		if (delta >= minDelta) {
			Log.w(TAG, "Updating steps");
			boolean stored = new DatabaseHelper(this)
					.registerActivityData(new ActivityData(delta));
			if (stored) {
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt("Steps", steps);
				editor.commit();
			}
		}
	}
}
