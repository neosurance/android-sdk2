package eu.neosurance.sdk;

import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

public class NSRLocationCallback extends LocationCallback {
	private NSR nsr;

	public NSRLocationCallback(NSR nsr) {
		super();
		this.nsr = nsr;
	}

	public void onLocationResult(LocationResult locationResult) {
		Location lastLocation = locationResult.getLastLocation();
		if (lastLocation != null) {
			nsr.stopTraceLocation();
			try {
				Log.d(NSR.TAG, "NSRLocationCallback: " + lastLocation);
				if (nsr.getLastLocation() != null) {
					Log.d(NSR.TAG, "NSRLocationCallback distanceTo: " + lastLocation.distanceTo(nsr.getLastLocation()));
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
				Log.e(NSR.TAG, "NSRLocationCallback", e);
			}
		} else {
			Log.d(NSR.TAG, "NSRLocationCallback: no result");
		}
	}
}
