package eu.neosurance.nsrsample;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Properties;

import eu.neosurance.sdk.NSR;
import eu.neosurance.sdk.NSRLog;
import eu.neosurance.sdk.NSRSecurityResponse;
import eu.neosurance.sdk.NSRUser;

public class MainActivity extends AppCompatActivity {
	public final static String TAG = "NSRSample";
	private WebView mainView;
	private Properties config;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			config = new Properties();
			config.load(this.getAssets().open("config.properties"));

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
		} catch (Exception e) {
		}
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
				if ("sendEvent2".equals(what)) {
					sendEvent2();
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
				if ("resetCruncher".equals(what)) {
					NSR.getInstance(this).resetCruncher();
				}
				if ("closeView".equals(what)) {
					NSR.getInstance(this).closeView();
				}
				if ("policies".equals(what)) {
					JSONObject criteria = new JSONObject();
					criteria.put("available",true);
					NSR.getInstance(this).policies(criteria, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error == null) {
								NSRLog.d(TAG, "policies response");
								NSRLog.d(TAG, json.toString());
							} else {
								NSRLog.e(TAG, "policies error: " + error);
							}
						}
					});
				}
			}
		} catch (Exception e) {
		}
	}


	public void registerUser() {
		try {
			Log.d(TAG, "registerUser");
			NSRUser user = new NSRUser();
			user.setEmail(config.getProperty("user.email"));
			user.setCode(config.getProperty("user.code"));
			user.setFirstname(config.getProperty("user.firstname"));
			user.setLastname(config.getProperty("user.lastname"));
			JSONObject locals = new JSONObject();
			locals.put("firstname",config.getProperty("user.firstname"));
			locals.put("lastname",config.getProperty("user.lastname"));
			locals.put("email",config.getProperty("user.email"));
			user.setLocals(locals);
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
			NSR.getInstance(this).sendEvent("ondemand", new JSONObject());
		} catch (Exception e) {
			Log.e(TAG, "sendEvent", e);
		}
	}

	public void sendEvent2() {
		try {
			Log.d(TAG, "sendEvent2");
			NSR.getInstance(this).sendEvent("testpush", new JSONObject());
		} catch (Exception e) {
			Log.e(TAG, "sendEvent2", e);
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

			settings.put("disable_log", false);
			settings.put("base_url", config.getProperty("base_url"));
			settings.put("code", config.getProperty("code"));
			settings.put("secret_key", config.getProperty("secret_key"));
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
