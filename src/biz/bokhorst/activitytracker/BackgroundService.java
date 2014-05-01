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
import android.app.AlarmManager;
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
import android.os.SystemClock;
import android.util.Log;

import biz.bokhorst.activitytracker.DatabaseHelper.ActivityData;
import biz.bokhorst.activitytracker.DatabaseHelper.ActivityRecord;

public class BackgroundService extends IntentService {
	private static String TAG = "ATRACKER";

	private static PendingIntent watchdogPendingIntent = null;
	private static ActivityRecognitionClient activityRecognitionClient = null;
	private static PendingIntent locationPendingIntent = null;
	private static boolean stepCounterRegistered = false;

	public static final String ACTION_INIT = "Init";
	public static final String ACTION_WATCHDOG = "Watchdog";
	public static final String ACTION_LOCATION = "Location";
	public static final String ACTION_STEPS = "Steps";

	public BackgroundService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// Handle intent
		try {
			if (ActivityRecognitionResult.hasResult(intent))
				handleActivityRecognition(intent);
			else if (ACTION_INIT.equals(intent.getAction()))
				handleInit(intent);
			else if (ACTION_WATCHDOG.equals(intent.getAction()))
				handleWatchdog(intent);
			else if (ACTION_LOCATION.equals(intent.getAction()))
				handleLocationChanged(intent);
			else if (ACTION_STEPS.equals(intent.getAction()))
				handleStepsChanged(intent);
			else
				Log.w(TAG, "Unknown intent=" + intent);
		} catch (Throwable ex) {
			Log.e(TAG, ex.toString());
		} finally {
			// Start trackers
			ensureWatchdog();
			ensureActivityRecognition();
			ensureLocationUpdates();
			ensureStepCounting();
		}
	}

	// Watchdog setup

	private void ensureWatchdog() {
		if (watchdogPendingIntent == null) {
			// TODO: get settings
			long interval = 60L * 1000L;

			// Build pending intent
			Intent watchdogIntent = new Intent(this, BackgroundService.class);
			watchdogIntent.setAction(BackgroundService.ACTION_WATCHDOG);
			watchdogPendingIntent = PendingIntent.getService(this, 0,
					watchdogIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			// Setup watchdog
			AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
			long watchdogTime = SystemClock.elapsedRealtime() + interval;
			alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					watchdogTime, watchdogPendingIntent);
			Log.w(TAG, "Watchdog started");
		}
	}

	// Activity recognition setup

	private void ensureActivityRecognition() {
		// Check for Play services
		int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (result == ConnectionResult.SUCCESS) {
			// Create activity recognition client
			if (activityRecognitionClient == null) {
				PlayServicesListener listener = new PlayServicesListener();
				activityRecognitionClient = new ActivityRecognitionClient(this,
						listener, listener);
			}

			// Connect to Play services
			if (!activityRecognitionClient.isConnected()
					&& !activityRecognitionClient.isConnecting()) {
				Log.w(TAG, "Connecting to activity recognition client");
				activityRecognitionClient.connect();
			}
		} else
			Log.w(TAG, "Play services not available, result=" + result);
	}

	private class PlayServicesListener implements ConnectionCallbacks,
			OnConnectionFailedListener {
		@Override
		public void onConnectionFailed(ConnectionResult result) {
			Log.e(TAG,
					"Connection to Play services failed, result="
							+ result.getErrorCode());
		}

		@Override
		public void onConnected(Bundle hint) {
			// TODO: settings
			int interval = 60 * 1000;

			// Build pending intent
			Intent activityIntent = new Intent(BackgroundService.this,
					BackgroundService.class);
			PendingIntent activityCallbackIntent = PendingIntent.getService(
					BackgroundService.this, 0, activityIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);

			// Request activity updates
			activityRecognitionClient.requestActivityUpdates(interval,
					activityCallbackIntent);
			Log.w(TAG, "Requested activity updates");
		}

		@Override
		public void onDisconnected() {
			Log.w(TAG, "Disconnected from Play services");
		}
	}

	// Location updates setup

	private void ensureLocationUpdates() {
		if (locationPendingIntent == null) {
			// TODO: settings
			int locationAccuracy = Criteria.POWER_LOW;
			int minTime = 60 * 1000;
			int minDistance = 50;

			// Build pending intent
			Intent locationIntent = new Intent(this, BackgroundService.class);
			locationIntent.setAction(BackgroundService.ACTION_LOCATION);
			locationPendingIntent = PendingIntent.getService(this, 0,
					locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			// Request location updates
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locationManager.removeUpdates(locationPendingIntent);
			Criteria criteria = new Criteria();
			criteria.setAccuracy(locationAccuracy);
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
				// TODO: settings
				int stepDelay = SensorManager.SENSOR_DELAY_NORMAL;

				// Request step counting
				SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
				Sensor countSensor = sensorManager
						.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
				sensorManager.registerListener(new SensorListener(),
						countSensor, stepDelay);
				stepCounterRegistered = true;
				Log.w(TAG, "Step counter listener registered");
			}
		} else
			Log.w(TAG, "No hardware step counter");
	}

	private class SensorListener implements SensorEventListener {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// Ignored
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
				int steps = (int) event.values[0];
				Log.w(TAG, "Steps changed, steps=" + steps);
				Intent intentSteps = new Intent(BackgroundService.this,
						BackgroundService.class);
				intentSteps.setAction(ACTION_STEPS);
				intentSteps.putExtra(ACTION_STEPS, steps);
				startService(intentSteps);
			}
		}
	}

	// Logic

	private void handleInit(Intent intent) {
		Log.w(TAG, "Init");
	}

	private void handleWatchdog(Intent intent) {
		Log.w(TAG, "Watchdog");
	}

	private void handleActivityRecognition(Intent intent) {
		DatabaseHelper dh = new DatabaseHelper(this);

		// Register activity
		ActivityRecognitionResult result = ActivityRecognitionResult
				.extractResult(intent);
		DetectedActivity mostProbableActivity = result
				.getMostProbableActivity();
		Log.w(TAG, "Activity type=" + mostProbableActivity.getType()
				+ " confidence=" + mostProbableActivity.getConfidence());

		boolean newActivity = false;
		if (mostProbableActivity.getType() != DetectedActivity.TILTING)
			newActivity = dh.registerActivityRecord(new ActivityRecord(result
					.getTime(), mostProbableActivity.getType()));
		for (DetectedActivity activity : result.getProbableActivities())
			dh.registerActivityData(new ActivityData(result.getTime(), activity
					.getType(), activity.getConfidence()));

		// Get last know location
		if (newActivity) {
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			Location location = locationManager
					.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
			dh.registerActivityData(new ActivityData(
					ActivityData.TYPE_TRACKPOINT, location));
		}
	}

	private void handleLocationChanged(Intent intent) {
		// Get location
		Location location = (Location) intent.getExtras().get(
				LocationManager.KEY_LOCATION_CHANGED);

		// Register location
		Log.w(TAG, "Location=" + location);
		new DatabaseHelper(this).registerActivityData(new ActivityData(
				ActivityData.TYPE_TRACKPOINT, location));
	}

	private void handleStepsChanged(Intent intent) {
		// TODO: setting
		int minStepDelta = 10;

		// TODO: shared preference
		SharedPreferences prefs = getSharedPreferences("activity",
				Context.MODE_MULTI_PROCESS);

		// Get steps
		int steps = intent.getIntExtra(ACTION_STEPS, -1);
		int last = prefs.getInt("Steps", 0);
		int delta = steps - last;
		Log.w(TAG, "Steps=" + steps + " Delta=" + delta + " Register="
				+ (delta >= minStepDelta));

		// Register steps
		if (delta >= minStepDelta) {
			new DatabaseHelper(this).registerActivityData(new ActivityData(
					delta));

			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt("Steps", steps);
			editor.commit();
		}
	}
}
