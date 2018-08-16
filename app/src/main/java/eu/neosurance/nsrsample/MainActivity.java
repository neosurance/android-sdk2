package eu.neosurance.nsrsample;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import eu.neosurance.sdk.NSR;
import eu.neosurance.sdk.NSRNotification;
import eu.neosurance.sdk.NSRUser;
import eu.neosurance.sdk.NSRWorkflowDelegate;

public class MainActivity extends AppCompatActivity {
	public final static String TAG = "NSRSample";
	private WFDelegate workflowDelegate;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setup(null);
	}

	public void registerUser(View v) {
		Log.d(TAG, "registerUser");
		NSRUser user = new NSRUser();
		user.setEmail("tg@neosurance.eu");
		user.setCode("tg@neosurance.eu");
		user.setFirstname("gio");
		user.setLastname("gio");
		user.setFiscalCode("ABCDE");
		NSR.getInstance(this).registerUser(user);
	}

	public void forgetUser(View v) {
		Log.d(TAG, "forgetUser");
		NSR.getInstance(this).forgetUser();
	}

	public void sendEvent(View v) {
		try {
			Log.d(TAG, "sendEvent");
			JSONObject payload = new JSONObject();
			payload.put("type", "*");
			NSR.getInstance(this).sendEvent("test", payload);
		} catch (Exception e) {
			Log.e(TAG, "sendEvent", e);
		}
	}

	public void policies(View v) {
		Log.d(TAG, "policies");
		NSR.getInstance(this).showApp();
	}

	public void setup(View v) {
		Log.d(TAG, "setup");
		try {
			JSONObject settings = new JSONObject();
			settings.put("base_url", "https://sandbox.neosurancecloud.net/sdk/api/v1.0/");
			settings.put("code", "ing");
			settings.put("secret_key", "uBc4dyQeqp7miIAfis");
			settings.put("push_icon", R.drawable.king);
			settings.put("ask_permission", 1);
			settings.put("dev_mode", 1);
			workflowDelegate = new WFDelegate();
			NSR.getInstance(this).setWorkflowDelegate(workflowDelegate);
			NSR.getInstance(this).setup(settings);
		} catch (JSONException e) {
			Log.e(TAG, "setup", e);
		}
	}

	public void appLogin(View v){
		Log.d(TAG, "appLogin");
		if(workflowDelegate.url != null){
			NSR.getInstance(this).loginExecuted(workflowDelegate.url);
			workflowDelegate.url = null;
		}
	}

	public void appPayment(View v){
		Log.d(TAG, "appPayment");
		try{
			if(workflowDelegate.url != null) {
				JSONObject paymentInfo = new JSONObject();
				paymentInfo.put("transactionCode", "abcde");
				NSR.getInstance(this).paymentExecuted(paymentInfo, workflowDelegate.url);
				workflowDelegate.url = null;
			}
		}catch(Exception e){
			Log.e(TAG, "appPayment", e);
		}
	}
}
