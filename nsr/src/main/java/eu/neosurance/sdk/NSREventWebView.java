package eu.neosurance.sdk;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class NSREventWebView {
	private WebView webView = null;
	private Context ctx;
	private NSR nsr;

	protected NSREventWebView(Context ctx, NSR nsr) {
		try {
			this.ctx = ctx;
			this.nsr = nsr;
			webView = new WebView(ctx);
			webView.addJavascriptInterface(this, "NSR");
			webView.getSettings().setJavaScriptEnabled(true);
			webView.getSettings().setAllowFileAccessFromFileURLs(true);
			webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
			webView.getSettings().setDomStorageEnabled(true);
			webView.loadUrl("file:///android_asset/eventCrucher.html");
		} catch (Exception e) {
			Log.e(NSR.TAG, e.getMessage(), e);
		}
	}

	protected void synch() {
		eval("synch()");
	}

	protected void crunchEvent(final String event, final JSONObject payload) {
		try {
			JSONObject nsrEvent = new JSONObject();
			nsrEvent.put("event", event);
			nsrEvent.put("payload", payload);
			eval("crunchEvent(" + nsrEvent.toString() + ")");
		} catch (JSONException e) {
			Log.e(NSR.TAG, "crunchEvent", e);
		}
	}

	protected void eval(final String code) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			public void run() {
				webView.evaluateJavascript(code, null);
			}
		});
	}

	@JavascriptInterface
	public void postMessage(final String json) {
		try {
			final JSONObject body = new JSONObject(json);
			if (body.has("log")) {
				Log.i(NSR.TAG, body.getString("log"));
			}
			if (body.has("event") && body.has("payload")) {
				nsr.sendEvent(body.getString("event"), body.getJSONObject("payload"));
			}
			if (body.has("action")) {
				nsr.sendAction(body.getString("action"), body.getString("code"), body.getString("details"));
			}
			if (body.has("what")) {
				if ("init".equals(body.getString("what")) && body.has("callBack")) {
					nsr.authorize(new NSRAuth() {
						public void authorized(boolean authorized) throws Exception {
							JSONObject settings = nsr.getSettings();
							JSONObject message = new JSONObject();
							message.put("api", settings.getString("base_url"));
							message.put("token", nsr.getToken());
							message.put("lang", nsr.getLang());
							message.put("deviceUid", nsr.getDeviceUid());
							eval(body.getString("callBack") + "(" + message.toString() + ")");
						}
					});
				}
				if ("token".equals(body.getString("what")) && body.has("callBack")) {
					nsr.authorize(new NSRAuth() {
						public void authorized(boolean authorized) throws Exception {
							JSONObject settings = nsr.getSettings();
							eval(body.getString("callBack") + "('" + nsr.getToken() + "')");
						}
					});
				}
				if ("user".equals(body.getString("what")) && body.has("callBack")) {
					eval(body.getString("callBack") + "(" + nsr.getUser().toJsonObject(true).toString() + ")");
				}
				if ("push".equals(body.getString("what")) && body.has("title") && body.has("body")) {
					String imageUrl = body.has("imageUrl") ? body.getString("imageUrl") : null;
					NSRNotification.sendNotification(ctx, body.getString("title"), body.getString("body"), imageUrl, null);
				}
				if ("geoCode".equals(body.getString("what")) && body.has("location") && body.has("callBack")) {
					Geocoder geocoder = new Geocoder(ctx, Locale.forLanguageTag(nsr.getLang()));
					JSONObject location = body.getJSONObject("location");
					List<Address> addresses = geocoder.getFromLocation(location.getDouble("latitude"), location.getDouble("longitude"), 1);
					if (addresses != null && addresses.size() > 0) {
						Address adr = addresses.get(0);
						JSONObject address = new JSONObject();
						address.put("countryCode", adr.getCountryCode().toUpperCase());
						address.put("countryName", adr.getCountryName());
						String adrLine = adr.getAddressLine(0);
						address.put("address", adrLine != null ? adrLine : "");
						eval(body.getString("callBack") + "(" + address.toString() + ")");
					}
				}
			}
		} catch (Exception e) {
			Log.e(NSR.TAG, "postMessage", e);
		}
	}
}
