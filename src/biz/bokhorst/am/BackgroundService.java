package biz.bokhorst.am;

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

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class BackgroundService extends IntentService implements
		ConnectionCallbacks, OnConnectionFailedListener {

	private static String TAG = "AM";
	private static PendingIntent locationPendingIntent = null;
	private static ActivityRecognitionClient activityRecognitionClient = null;

	public static final String ACTION_START = "Start";
	public static final String ACTION_LOCATION = "Location";

	public BackgroundService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.w(TAG, "Handling intent, action=" + intent.getAction());

		// Start activity recognition (again)
		ensureActivityRecognition();

		// Start location updates (again)
		ensureLocationUpdates();

		// Check for activity recognition result
		if (ActivityRecognitionResult.hasResult(intent))
			handleActivityRecognition(intent);
		else if (ACTION_LOCATION.equals(intent.getAction()))
			handleLocationChanged(intent);
	}

	private void handleActivityRecognition(Intent intent) {
		ActivityRecognitionResult result = ActivityRecognitionResult
				.extractResult(intent);
		DetectedActivity mostProbableActivity = result
				.getMostProbableActivity();
		int confidence = mostProbableActivity.getConfidence();
		int activityType = mostProbableActivity.getType();

		String activityName = getNameFromType(activityType);
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
	}

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

	private void ensureLocationUpdates() {
		if (locationPendingIntent == null) {
			// Build pending intent
			Intent locationIntent = new Intent(this, BackgroundService.class);
			locationIntent.setAction(BackgroundService.ACTION_LOCATION);

			locationPendingIntent = PendingIntent.getService(this, 0,
					locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			// Request location updates
			Log.w(TAG, "Requesting location updates");
			LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locationManager.removeUpdates(locationPendingIntent);
			int minTime = 60 * 1000; // TODO: setting
			int minDistance = 50; // TODO: setting
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			locationManager.requestLocationUpdates(minTime, minDistance,
					criteria, locationPendingIntent);
		}
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		Log.e(TAG,
				"Connection to Play services failed, result="
						+ result.getErrorCode());
	}

	@Override
	public void onConnected(Bundle hint) {
		Log.w(TAG, "Connected to Play services");
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
