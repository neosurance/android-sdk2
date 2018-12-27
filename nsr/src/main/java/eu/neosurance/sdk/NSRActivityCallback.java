package eu.neosurance.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class NSRActivityCallback extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		NSRLog.d(NSR.TAG, "NSRActivityCallback");
		final NSR nsr = NSR.getInstance(context);
		JSONObject conf = nsr.getConf();
		if (conf == null)
			return;
		nsr.opportunisticTrace();
		if (ActivityRecognitionResult.hasResult(intent)) {
			NSRLog.d(NSR.TAG, "NSRActivityCallback hasResult");

			Map<String, Integer> confidences = new HashMap<>();
			Map<String, Integer> counts = new HashMap<>();
			String candidate = null;
			int maxConfidence = 0;
			try {
				for (DetectedActivity activity : ActivityRecognitionResult.extractResult(intent).getProbableActivities()) {
					NSRLog.d(NSR.TAG, "activity: " + activityType(activity) + " - " + activity.getConfidence());
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
				int minConfidence = conf.getJSONObject("activity").getInt("confidence");
				NSRLog.d(NSR.TAG, "candidate: " + candidate);
				NSRLog.d(NSR.TAG, "maxConfidence: " + maxConfidence);
				NSRLog.d(NSR.TAG, "minConfidence: " + minConfidence);
				NSRLog.d(NSR.TAG, "lastActivity: " + nsr.getLastActivity());
				if (candidate != null && !candidate.equals(nsr.getLastActivity()) && maxConfidence >= minConfidence) {
					NSRLog.d(NSR.TAG, "NSRActivityCallback sending");
					JSONObject payload = new JSONObject();
					payload.put("type", candidate);
					payload.put("confidence", maxConfidence);
					nsr.crunchEvent("activity", payload);
					nsr.setLastActivity(candidate);
					NSRLog.d(NSR.TAG, "NSRActivityCallback sent");
				}
			} catch (Exception e) {
				NSRLog.e(NSR.TAG, "NSRActivityCallback", e);
			}
		} else {
			NSRLog.d(NSR.TAG, "NSRActivityCallback: no result");
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
