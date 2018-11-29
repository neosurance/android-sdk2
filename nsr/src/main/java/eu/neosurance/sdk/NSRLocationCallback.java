package eu.neosurance.sdk;

import android.location.Location;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

public class NSRLocationCallback extends LocationCallback {
	private NSR nsr;

	public NSRLocationCallback(NSR nsr) {
		this.nsr = nsr;
	}

	public void onLocationResult(LocationResult locationResult) {
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		nsr.opportunisticTrace();
		nsr.checkHardTraceLocation();
		try {
			Location lastLocation = locationResult.getLastLocation();
			NSRLog.d(NSR.TAG, "NSRLocationCallback: " + lastLocation);
			NSRLog.d(NSR.TAG, "NSRLocationCallback sending");
			JSONObject payload = new JSONObject();
			payload.put("latitude", lastLocation.getLatitude());
			payload.put("longitude", lastLocation.getLongitude());
			payload.put("altitude", lastLocation.getAltitude());
			nsr.crunchEvent("position", payload);
			nsr.setStillLocationSent(false);
			NSRLog.d(NSR.TAG, "NSRLocationCallback sent");
		} catch (Exception e) {
			NSRLog.e(NSR.TAG, "NSRLocationCallback", e);
		}
	}
}
