package eu.neosurance.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationResult;

import org.json.JSONObject;

public class NSRLocationCallback extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		NSR nsr = NSR.getInstance(context);
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		nsr.opportunisticTrace();
		if (LocationResult.hasResult(intent)) {
			try {
				LocationResult lr = LocationResult.extractResult(intent);
				Location lastLocation = lr.getLastLocation();
				Log.d(NSR.TAG, "NSRLocationCallback: " + lastLocation);
				if (nsr.getLastLocation() != null) {
					Log.d(NSR.TAG, "NSRLocationCallback distanceTo: " + lastLocation.distanceTo(nsr.getLastLocation()));
				}
				double meters = conf.getJSONObject("position").getDouble("meters");
				if (nsr.getLastLocation() == null || lastLocation.distanceTo(nsr.getLastLocation()) > meters) {
					Log.d(NSR.TAG, "NSRLocationCallback sending");
					JSONObject payload = new JSONObject();
					payload.put("latitude", lastLocation.getLatitude());
					payload.put("longitude", lastLocation.getLongitude());
					payload.put("altitude", lastLocation.getAltitude());
					nsr.crunchEvent("position", payload);
					nsr.setLastLocation(lastLocation);
					nsr.setStillLocationSent(false);
					Log.d(NSR.TAG, "NSRLocationCallback sent");

				}
			} catch (Exception e) {
				Log.e(NSR.TAG, "NSRLocationCallback", e);
			}
		} else {
			Log.d(NSR.TAG, "NSRLocationCallback: no result");
		}
	}
}
