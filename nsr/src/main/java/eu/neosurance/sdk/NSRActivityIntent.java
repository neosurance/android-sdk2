package eu.neosurance.sdk;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class NSRActivityIntent extends IntentService {

	public NSRActivityIntent() {
		super("NSRActivityIntent");
	}

	@Override
	public void onHandleIntent(Intent intent) {
		Log.d(NSR.TAG, "NSRActivityIntent");
		NSR nsr = NSR.getInstance(getApplicationContext());
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		if (ActivityRecognitionResult.hasResult(intent)) {
			nsr.stopTraceActivity();
			Map<String, Integer> confidences = new HashMap();
			Map<String, Integer> counts = new HashMap();
			String candidate = null;
			int maxConfidence = 0;
			try {
				for (DetectedActivity activity : ActivityRecognitionResult.extractResult(intent).getProbableActivities()) {
					Log.d(NSR.TAG, "activity: " + activityType(activity) + " - " + activity.getConfidence());
					String type = activityType(activity);
					if (type != null) {
						int confidence = (confidences.get(type) != null ? confidences.get(type).intValue() : 0) + activity.getConfidence();
						confidences.put(type, confidence);
						int count = (counts.get(type) != null ? counts.get(type).intValue() : 0) + 1;
						counts.put(type, count);
						int weightedConfidence = confidence / count + (count * 5);
						if (weightedConfidence > maxConfidence) {
							candidate = type;
							maxConfidence = weightedConfidence;
						}
					}
				}
				if (maxConfidence > 100) {
					maxConfidence = 100;
				}
				int minConfidene = conf.getJSONObject("activity").getInt("confidence");
				Log.d(NSR.TAG, "candidate: " + candidate);
				Log.d(NSR.TAG, "maxConfidence: " + maxConfidence);
				Log.d(NSR.TAG, "minConfidene: " + minConfidene);
				Log.d(NSR.TAG, "lastActivity: " + nsr.getLastActivity());
				if (candidate != null && !candidate.equals(nsr.getLastActivity()) && maxConfidence >= minConfidene) {
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

	private String activityType(DetectedActivity activity) {
		switch (activity.getType()) {
			case DetectedActivity.STILL:
				return "still";
			case DetectedActivity.WALKING:
			case DetectedActivity.ON_FOOT:
				return "walk";
			case DetectedActivity.RUNNING:
				return "run";
			case DetectedActivity.ON_BICYCLE:
				return "bicycle";
			case DetectedActivity.IN_VEHICLE:
				return "car";
		}
		return null;
	}
}
