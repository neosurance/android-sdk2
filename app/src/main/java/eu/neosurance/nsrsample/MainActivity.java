package eu.neosurance.nsrsample;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import eu.neosurance.sdk.NSR;
import eu.neosurance.sdk.NSRUser;

public class MainActivity extends AppCompatActivity {
	public final static String TAG = "NSRSample";
	private WebView mainView;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		WebView.setWebContentsDebuggingEnabled(true);
		mainView = new WebView(this);
		mainView.getSettings().setJavaScriptEnabled(true);
		mainView.getSettings().setDomStorageEnabled(true);
		mainView.getSettings().setAllowFileAccessFromFileURLs(true);
		mainView.getSettings().setAllowUniversalAccessFromFileURLs(true);
		mainView.addJavascriptInterface(this, "app");
		mainView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

		setContentView(mainView);
		mainView.loadUrl("file:///android_asset/sample.html");

		setup();
	}

	protected void eval(final String code) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			public void run() {
				mainView.evaluateJavascript(code, null);
			}
		});
	}

	@JavascriptInterface
	public void postMessage(final String json) {
		try {
			final JSONObject body = new JSONObject(json);
			String what = body.getString("what");
			if (what != null) {
				if ("setup".equals(what)) {
					setup();
				}
				if ("registerUser".equals(what)) {
					registerUser();
				}
				if ("forgetUser".equals(what)) {
					forgetUser();
				}
				if ("showApp".equals(what)) {
					showApp();
				}
				if ("sendEvent".equals(what)) {
					sendEvent();
				}
				if ("appLogin".equals(what)) {
					appLogin();
				}
				if ("appPayment".equals(what)) {
					appPayment();
				}
				if ("accurateLocation".equals(what)) {
					NSR.getInstance(this).accurateLocation(0, 20, true);
				}
				if ("accurateLocationEnd".equals(what)) {
					NSR.getInstance(this).accurateLocationEnd();
				}
			}
		} catch (Exception e) {
		}
	}


	public void registerUser() {
		try {
			Log.d(TAG, "registerUser");
			NSRUser user = new NSRUser();
			user.setEmail("AE.testANDROID@neosurance.eu");
			user.setCode("AE.testANDROID@neosurance.eu");
			user.setFirstname("testANDROID");
			user.setLastname("testANDROID");

			NSR.getInstance(this).registerUser(user);
		} catch (Exception e) {
		}
	}

	public void forgetUser() {
		Log.d(TAG, "forgetUser");
		NSR.getInstance(this).forgetUser();
	}

	public void sendEvent() {
		try {
			Log.d(TAG, "sendEvent");
			JSONObject payload = new JSONObject();
			payload.put("fromCode", "IT");
			payload.put("fromCountry", "italia");
			payload.put("toCode", "FR");
			payload.put("toCountry", "francia");
			payload.put("fake", 1);
			NSR.getInstance(this).sendEvent("countryChange", payload);
		} catch (Exception e) {
			Log.e(TAG, "sendEvent", e);
		}
	}

	public void showApp() {
		Log.d(TAG, "showApp");
		NSR.getInstance(this).showApp();
	}

	public void setup() {
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

	public void appLogin() {
		Log.d(TAG, "appLogin");
		String url = WFDelegate.getData(this, "login_url");
		if (url != null) {
			NSR.getInstance(this).loginExecuted(url);
			WFDelegate.setData(this, "login_url", null);
		}
	}

	public void appPayment() {
		Log.d(TAG, "appPayment");
		try {
			String url = WFDelegate.getData(this, "payment_url");
			if (url != null) {
				JSONObject paymentInfo = new JSONObject();
				paymentInfo.put("transactionCode", "abcde");
				NSR.getInstance(this).paymentExecuted(paymentInfo, url);
				WFDelegate.setData(this, "payment_url", null);
			}
		} catch (Exception e) {
			Log.e(TAG, "appPayment", e);
		}
	}

	public void timeline() {
		Log.d(TAG, "timeline");
		NSR.getInstance(this).showUrl("https://s3.eu-west-2.amazonaws.com/neosurancesandbox/apps/timeline/app.html");
	}

}
