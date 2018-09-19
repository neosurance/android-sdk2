package eu.neosurance.sdk;

import android.Manifest;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class NSRActivityIntent extends IntentService {
	private static final String ID = "NSRActivityIntent";

	public NSRActivityIntent() {
		super("NSRActivityIntent");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.createNotificationChannel(new NotificationChannel(ID, ID, NotificationManager.IMPORTANCE_NONE));
			NotificationCompat.Builder notification = new NotificationCompat.Builder(this, ID);
			notification.setPriority(NotificationCompat.PRIORITY_MIN);
			startForeground(1, notification.build());
		}
	}

	@Override
	public void onHandleIntent(Intent intent) {
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.deleteNotificationChannel(ID);
		}
		Log.d(NSR.TAG, "NSRActivityIntent");
		final NSR nsr = NSR.getInstance(getApplicationContext());
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		nsr.opportunisticTrace();
		if (ActivityRecognitionResult.hasResult(intent)) {
			Map<String, Integer> confidences = new HashMap();
			Map<String, Integer> counts = new HashMap();
			String candidate = null;
			int maxConfidence = 0;
			try {
				for (DetectedActivity activity : ActivityRecognitionResult.extractResult(intent).getProbableActivities()) {
					Log.d(NSR.TAG, "activity: " + activityType(activity) + " - " + activity.getConfidence());
					String type = activityType(activity);
					if (type != null) {
						int confidence = (confidences.get(type) != null ? confidences.get(type).intValue() : 0) + activity.getConfidence();
						confidences.put(type, confidence);
						int count = (counts.get(type) != null ? counts.get(type).intValue() : 0) + 1;
						counts.put(type, count);
						int weightedConfidence = confidence / count + (count * 5);
						if (weightedConfidence > maxConfidence) {
							candidate = type;
							maxConfidence = weightedConfidence;
						}
					}
				}
				if (maxConfidence > 100) {
					maxConfidence = 100;
				}
				int minConfidence = conf.getJSONObject("activity").getInt("confidence");
				Log.d(NSR.TAG, "candidate: " + candidate);
				Log.d(NSR.TAG, "maxConfidence: " + maxConfidence);
				Log.d(NSR.TAG, "minConfidence: " + minConfidence);
				Log.d(NSR.TAG, "lastActivity: " + nsr.getLastActivity());
				if (candidate != null && !candidate.equals(nsr.getLastActivity()) && maxConfidence >= minConfidence) {
					JSONObject payload = new JSONObject();
					payload.put("type", candidate);
					payload.put("confidence", maxConfidence);
					nsr.crunchEvent("activity", payload);
					nsr.setLastActivity(candidate);

					if (!nsr.getStillLocationSent() && "still".equals(candidate) && NSR.getBoolean(conf.getJSONObject("position"), "enabled")) {
						Log.d(NSR.TAG, "StillLocation");
						boolean coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
						boolean fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
						if (coarse && fine) {
							final FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);
							LocationRequest locationRequest = LocationRequest.create();
							locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
							locationRequest.setInterval(0);
							locationRequest.setNumUpdates(1);
							locationClient.requestLocationUpdates(locationRequest,
								new LocationCallback() {
									public void onLocationResult(LocationResult locationResult) {
										Log.d(NSR.TAG, "StillLocation onLocationResult");
										Location lastLocation = locationResult.getLastLocation();
										if (lastLocation != null) {
											locationClient.removeLocationUpdates(this);
											try {
												JSONObject payload = new JSONObject();
												payload.put("latitude", lastLocation.getLatitude());
												payload.put("longitude", lastLocation.getLongitude());
												payload.put("altitude", lastLocation.getAltitude());
												Log.d(NSR.TAG, "StillLocation: " + payload);
												nsr.crunchEvent("position", payload);
												nsr.setLastLocation(lastLocation);
												nsr.setStillLocationSent(true);
											} catch (Exception e) {
											}
										}
									}
								}, Looper.getMainLooper());
						}
					}
				}
			} catch (Exception e) {
				Log.e(NSR.TAG, "NSRActivityIntent", e);
			}
		} else {
			Log.d(NSR.TAG, "NSRActivityIntent: no result");
		}
	}

	private String activityType(DetectedActivity activity) {
		switch (activity.getType()) {
			case DetectedActivity.STILL:
				return "still";
			case DetectedActivity.WALKING:
			case DetectedActivity.ON_FOOT:
				return "walk";
			case DetectedActivity.RUNNING:
				return "run";
			case DetectedActivity.ON_BICYCLE:
				return "bicycle";
			case DetectedActivity.IN_VEHICLE:
				return "car";
		}
		return null;
	}
}
