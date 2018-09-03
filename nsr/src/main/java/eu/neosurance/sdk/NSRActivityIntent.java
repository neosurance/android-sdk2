package eu.neosurance.sdk;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import org.json.JSONObject;

import java.util.List;

public class NSRActivityIntent extends IntentService {

	public NSRActivityIntent() {
		super("NSRActivityIntent");
	}

	public void onHandleIntent(Intent intent) {
		NSR nsr = NSR.getInstance(getApplicationContext());
		if (ActivityRecognitionResult.hasResult(intent)) {
			nsr.stopTraceActivity();
			String candidate = null;
			int maxConfidence = 0;
			try {
				List<DetectedActivity> pas = ActivityRecognitionResult.extractResult(intent).getProbableActivities();
				for (DetectedActivity da : pas) {
					String type = null;
					switch (da.getType()) {
						case DetectedActivity.IN_VEHICLE:
							type = "car";
							break;
						case DetectedActivity.ON_BICYCLE:
							type = "bicycle";
							break;
						case DetectedActivity.WALKING:
						case DetectedActivity.ON_FOOT:
							type = "walking";
							break;
						case DetectedActivity.RUNNING:
							type = "run";
							break;
						case DetectedActivity.STILL:
							type = "still";
							break;
					}
					Log.d(NSR.TAG, "activity: " + type + " - " + da.getConfidence());

					if (type != null && (da.getConfidence() > maxConfidence)) {
						candidate = type;
						maxConfidence = da.getConfidence();
					}
				}

				if (candidate != null && !candidate.equals(nsr.getLastActivity()) && maxConfidence >= nsr.getConf().getJSONObject("activity").getInt("confidence")) {
					JSONObject payload = new JSONObject();
					payload.put("type", candidate);
					payload.put("confidence", maxConfidence);
					nsr.crunchEvent("activity", payload);
					nsr.setLastActivity(candidate);
					nsr.setStillLocation("still".equals(candidate));
				}
			} catch (Exception e) {
				Log.e(NSR.TAG, "NSRActivityIntent", e);
			}
		} else {
			Log.d(NSR.TAG, "NSRActivityIntent: no result");
		}
	}
}
