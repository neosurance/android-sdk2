package eu.neosurance.sdk;

import android.location.Location;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

public class NSRLocationCallback extends LocationCallback {
	private NSR nsr;
	private FusedLocationProviderClient locationClient;

	public NSRLocationCallback(NSR nsr, FusedLocationProviderClient locationClient) {
		this.nsr = nsr;
		this.locationClient = locationClient;
	}

	public void onLocationResult(LocationResult locationResult) {
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		nsr.opportunisticTrace();
		nsr.checkHardTraceLocation();
		Location lastLocation = locationResult.getLastLocation();
		if (lastLocation != null) {
			try {
				if (locationClient != null) {
					locationClient.removeLocationUpdates(this);
				}
				NSRLog.d(NSR.TAG, "NSRLocationCallback: " + lastLocation);
				String bckLoc = lastLocation.getLatitude() + "|" + lastLocation.getLongitude();
				if (!bckLoc.equals(nsr.getBckLoc())) {
					nsr.setBckLoc(bckLoc);
					NSRLog.d(NSR.TAG, "NSRLocationCallback sending");
					JSONObject payload = new JSONObject();
					payload.put("latitude", lastLocation.getLatitude());
					payload.put("longitude", lastLocation.getLongitude());
					payload.put("altitude", lastLocation.getAltitude());
					nsr.crunchEvent("position", payload);
					NSRLog.d(NSR.TAG, "NSRLocationCallback sent");
				} else {
					NSRLog.d(NSR.TAG, "NSRLocationCallback already sent: " + bckLoc);
				}
			} catch (Exception e) {
				NSRLog.e(NSR.TAG, "NSRLocationCallback", e);
			}
		}
	}
}
