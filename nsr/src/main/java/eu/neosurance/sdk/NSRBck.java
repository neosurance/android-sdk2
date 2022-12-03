package eu.neosurance.sdk;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

public class NSRBck extends BroadcastReceiver {
	@Override
	public void onReceive(Context ctx, Intent intent) {
		final NSR nsr = NSR.getInstance(ctx);
		try {
			NSRLog.d(NSR.TAG, "NSRBck in");
			JSONObject conf = nsr.getConf();
			boolean fine = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			boolean coarse = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			if ((coarse || fine) && conf != null && nsr.getBoolean(conf.getJSONObject("position"), "enabled")) {
				nsr.initBck(conf.getInt("time"));
				final FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(ctx);
				LocationRequest locationRequest = LocationRequest.create();
				locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
				locationRequest.setInterval(0);
				locationRequest.setNumUpdates(1);
				locationClient.requestLocationUpdates(locationRequest, new NSRLocationCallback(nsr, locationClient), Looper.getMainLooper());
				NSRLog.d(NSR.TAG, "NSRBck locrequested");
			}
		} catch (Exception e) {
			nsr.initBck(30);
			NSRLog.e(NSR.TAG, "NSRBck err:", e);
		}
	}

}
