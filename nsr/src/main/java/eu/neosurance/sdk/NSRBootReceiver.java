package eu.neosurance.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NSRBootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		NSRLog.d(NSR.TAG, "NSRBootReceiver");
		NSR.getInstance(context);
	}
}
