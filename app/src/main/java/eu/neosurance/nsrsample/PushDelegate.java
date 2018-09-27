package eu.neosurance.nsrsample;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import eu.neosurance.sdk.NSRPushDelegate;

public class PushDelegate implements NSRPushDelegate {

	@Override
	public PendingIntent makePendingIntent(Context ctx, JSONObject push) {
		try {
			Intent intent = new Intent(ctx, PushService.class);
			intent.putExtra("code", push.getString("code"));
			intent.putExtra("expirationTime", push.getLong("expirationTime"));
			return PendingIntent.getService(ctx, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
		} catch (Exception e) {
			return null;
		}
	}
}
