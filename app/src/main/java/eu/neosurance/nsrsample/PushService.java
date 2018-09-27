package eu.neosurance.nsrsample;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class PushService extends IntentService {

	public PushService() {
		super("PushService");
	}

	@Override
	public void onHandleIntent(Intent intent) {
		String code = intent.getExtras().getString("code");
		Long expirationTime = intent.getExtras().getLong("expirationTime");
		Log.d(">>> code ", code + " expirationTime " + expirationTime);
	}
}