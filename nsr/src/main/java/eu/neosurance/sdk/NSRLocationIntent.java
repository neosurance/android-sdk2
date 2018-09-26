package eu.neosurance.sdk;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

public class NSRLocationIntent extends IntentService {
	public NSRLocationIntent() {
		super("NSRLocationIntent");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.createNotificationChannel(new NotificationChannel(NSR.SILENT_ID, NSR.SILENT_ID, NotificationManager.IMPORTANCE_NONE));
			NotificationCompat.Builder notification = new NotificationCompat.Builder(this, NSR.SILENT_ID);
			notification.setPriority(NotificationCompat.PRIORITY_MIN);
			startForeground(1, notification.build());
		}
	}

	@Override
	public void onHandleIntent(Intent intent) {
		NSR nsr = NSR.getInstance(getApplicationContext());
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		nsr.opportunisticTrace();
		if (LocationResult.hasResult(intent)) {
			try {
				LocationResult lr = LocationResult.extractResult(intent);
				Location lastLocation = lr.getLastLocation();
				Log.d(NSR.TAG, "NSRLocationIntent: " + lastLocation);
				if (nsr.getLastLocation() != null) {
					Log.d(NSR.TAG, "NSRLocationIntent distanceTo: " + lastLocation.distanceTo(nsr.getLastLocation()));
				}
				double meters = conf.getJSONObject("position").getDouble("meters");
				if (nsr.getLastLocation() == null || lastLocation.distanceTo(nsr.getLastLocation()) > meters) {
					JSONObject payload = new JSONObject();
					payload.put("latitude", lastLocation.getLatitude());
					payload.put("longitude", lastLocation.getLongitude());
					payload.put("altitude", lastLocation.getAltitude());
					nsr.crunchEvent("position", payload);
					nsr.setLastLocation(lastLocation);
					nsr.setStillLocationSent(false);
				}
			} catch (Exception e) {
				Log.e(NSR.TAG, "NSRLocationIntent", e);
			}
		} else {
			Log.d(NSR.TAG, "NSRLocationIntent: no result");
		}
	}
}
