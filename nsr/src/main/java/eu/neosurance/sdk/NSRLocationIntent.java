package eu.neosurance.sdk;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

public class NSRLocationIntent extends IntentService {

	public NSRLocationIntent() {
		super("NSRLocationIntent");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		NSR nsr = NSR.getInstance(getApplicationContext());
		if (LocationResult.hasResult(intent)) {
			nsr.stopTraceLocation();
			try {
				LocationResult lr = LocationResult.extractResult(intent);
				Location lastLocation = lr.getLastLocation();
				Log.d(NSR.TAG, "NSRLocationIntent: " + lastLocation);
				if (nsr.getLastLocation() != null) {
					Log.d(NSR.TAG, "NSRLocationIntent distanceTo: " + lastLocation.distanceTo(nsr.getLastLocation()));
				}
				if (nsr.getLastLocation() == null || nsr.getStillLocation() || lastLocation.distanceTo(nsr.getLastLocation()) > nsr.getConf().getJSONObject("position").getDouble("meters")) {
					JSONObject payload = new JSONObject();
					payload.put("latitude", lastLocation.getLatitude());
					payload.put("longitude", lastLocation.getLongitude());
					payload.put("altitude", lastLocation.getAltitude());
					nsr.crunchEvent("position", payload);
					nsr.setLastLocation(lastLocation);
					nsr.setStillLocation(false);
				}
			} catch (Exception e) {
				Log.e(NSR.TAG, "NSRLocationIntent", e);
			}
		} else {
			Log.d(NSR.TAG, "NSRLocationIntent: no result");
		}
	}
}
