package eu.neosurance.sdk;

import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

public class NSRJobService extends JobService {
	public boolean onStartJob(JobParameters jobParameters) {
		Log.d(NSR.TAG, "onStartJob");
		NSR nsr = NSR.getInstance(getApplicationContext());
		nsr.traceLocation();
		nsr.traceActivity();
		nsr.tracePower();
		nsr.traceConnection();
		return false;
	}

	public boolean onStopJob(JobParameters jobParameters) {
		Log.d(NSR.TAG, "onStopJob");
		return false;
	}
}