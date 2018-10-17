package eu.neosurance.nsrsample;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import eu.neosurance.sdk.NSR;
import eu.neosurance.sdk.NSRUser;

public class MainActivity extends AppCompatActivity {
	public final static String TAG = "NSRSample";

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setup(null);
	}

	public void registerUser(View v) {
		Log.d(TAG, "registerUser");
		NSRUser user = new NSRUser();
		user.setEmail("xxx@neosurance.eu");
		user.setCode("xxx@neosurance.eu");
		user.setFirstname("xxx");
		user.setLastname("xxxx");
		user.setFiscalCode("ABCDE");
		NSR.getInstance(this).registerUser(user);
	}

	public void forgetUser(View v) {
		Log.d(TAG, "forgetUser");
		NSR.getInstance(this).forgetUser();
	}

	public void sendEventTest(View v) {
		try {
			Log.d(TAG, "sendEvent");
			JSONObject payload = new JSONObject();
			payload.put("type", "*");
			NSR.getInstance(this).sendEvent("test", payload);
		} catch (Exception e) {
			Log.e(TAG, "sendEvent", e);
		}
	}

	public void sendEvent(View v) {
		try {
			Log.d(TAG, "sendEvent");
			JSONObject payload = new JSONObject();
			payload.put("fromCode", "IT");
			payload.put("fromCountry", "italia");
			payload.put("toCode", "FR");
			payload.put("toCountry", "francia");
			NSR.getInstance(this).sendEvent("countryChange", payload);
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
			settings.put("code", "<code>");
			settings.put("secret_key", "<secret_key>");
			settings.put("push_icon", R.drawable.king);
			settings.put("ask_permission", 1);
			settings.put("dev_mode", 1);
			NSR.getInstance(this).setWorkflowDelegate(new WFDelegate());
			//NSR.getInstance(this).setPushDelegate(new PushDelegate());
			NSR.getInstance(this).setup(settings);
		} catch (JSONException e) {
			Log.e(TAG, "setup", e);
		}
	}

	public void appLogin(View v) {
		Log.d(TAG, "appLogin");
		String url = WFDelegate.getData(this,"login_url");
		if (url != null) {
			NSR.getInstance(this).loginExecuted(url);
			WFDelegate.setData(this,"login_url",null);
		}
	}

	public void appPayment(View v) {
		Log.d(TAG, "appPayment");
		try {
			String url = WFDelegate.getData(this,"payment_url");
			if (url != null) {
				JSONObject paymentInfo = new JSONObject();
				paymentInfo.put("transactionCode", "abcde");
				NSR.getInstance(this).paymentExecuted(paymentInfo, url);
				WFDelegate.setData(this,"payment_url", null);
			}
		} catch (Exception e) {
			Log.e(TAG, "appPayment", e);
		}
	}

	public void timeline(View v) {
		Log.d(TAG, "timeline");
		NSR.getInstance(this).showUrl("https://s3.eu-west-2.amazonaws.com/neosurancesandbox/apps/timeline/app.html");
	}

}
