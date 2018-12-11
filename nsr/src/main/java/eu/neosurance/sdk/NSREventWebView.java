package eu.neosurance.sdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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

	@SuppressLint("SetJavaScriptEnabled")
	protected NSREventWebView(final Context ctx, final NSR nsr) {
		try {
			this.ctx = ctx;
			this.nsr = nsr;
			final NSREventWebView eventWebView = this;
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				public void run() {
					webView = new WebView(ctx);
					if (Build.VERSION.SDK_INT >= 21) {
						WebView.setWebContentsDebuggingEnabled(NSR.getBoolean(nsr.getSettings(), "dev_mode"));
					}
					webView.addJavascriptInterface(eventWebView, "NSR");
					webView.getSettings().setJavaScriptEnabled(true);
					webView.getSettings().setAllowFileAccessFromFileURLs(true);
					webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
					webView.getSettings().setDomStorageEnabled(true);
					webView.loadUrl("file:///android_asset/eventCruncher.html?ns_lang=" + nsr.getLang() + "&ns_log=" + nsr.isLogEnabled());
				}
			});
		} catch (Exception e) {
			NSRLog.e(NSR.TAG, e.getMessage(), e);
		}
	}

	protected void synch() {
		eval("EVC.synch()");
	}

	protected void reset() {
		eval("localStorage.clear();EVC.synch()");
	}

	protected void crunchEvent(final String event, final JSONObject payload) {
		try {
			JSONObject nsrEvent = new JSONObject();
			nsrEvent.put("event", event);
			nsrEvent.put("payload", payload);
			eval("EVC.innerCrunchEvent(" + nsrEvent.toString() + ")");
		} catch (JSONException e) {
			NSRLog.e(NSR.TAG, "crunchEvent", e);
		}
	}

	protected void eval(final String code) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			public void run() {
				try {
					if (webView != null && Build.VERSION.SDK_INT >= 21) {
						webView.evaluateJavascript(code, null);
					}
				} catch (Throwable e) {
				}
			}
		});
	}

	@JavascriptInterface
	public void postMessage(final String json) {
		try {
			final JSONObject body = new JSONObject(json);
			if (body.has("log")) {
				NSRLog.i(NSR.TAG, body.getString("log"));
			}
			if (body.has("event") && body.has("payload")) {
				nsr.sendEvent(body.getString("event"), body.getJSONObject("payload"));
			}
			if (body.has("archiveEvent") && body.has("payload")) {
				nsr.archiveEvent(body.getString("archiveEvent"), body.getJSONObject("payload"));
			}
			if (body.has("action")) {
				nsr.sendAction(body.getString("action"), body.getString("code"), body.getString("details"));
			}
			if (body.has("push")) {
				if (body.has("delay")) {
					nsr.showPush(body.has("id") ? body.getString("id") : Integer.toString((int) SystemClock.elapsedRealtime()), body.getJSONObject("push"), body.getInt("delay"));
				} else {
					nsr.showPush(body.getJSONObject("push"));
				}
			}
			if (body.has("killPush")) {
				nsr.killPush(body.getString("killPush"));
			}
			if (body.has("what")) {
				String what = body.getString("what");
				if ("continueInitJob".equals(what)) {
					nsr.continueInitJob();
				}
				if ("init".equals(what) && body.has("callBack")) {
					nsr.authorize(new NSRAuth() {
						public void authorized(boolean authorized) throws Exception {
							if (authorized) {
								JSONObject message = new JSONObject();
								message.put("api", nsr.getSettings().getString("base_url"));
								message.put("token", nsr.getToken());
								message.put("lang", nsr.getLang());
								message.put("deviceUid", nsr.getDeviceUid());
								eval(body.getString("callBack") + "(" + message.toString() + ")");
							}
						}
					});
				}
				if ("token".equals(what) && body.has("callBack")) {
					nsr.authorize(new NSRAuth() {
						public void authorized(boolean authorized) throws Exception {
							if (authorized) {
								eval(body.getString("callBack") + "('" + nsr.getToken() + "')");
							}
						}
					});
				}
				if ("user".equals(what) && body.has("callBack")) {
					eval(body.getString("callBack") + "(" + nsr.getUser().toJsonObject(true).toString() + ")");
				}
				if ("geoCode".equals(what) && body.has("location") && body.has("callBack")) {
					Geocoder geocoder = null;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
						geocoder = new Geocoder(ctx, Locale.forLanguageTag(nsr.getLang()));
					}
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
				if ("store".equals(what) && body.has("key") && body.has("data")) {
					nsr.setJSONData(body.getString("key"), body.getJSONObject("data"));
				}
				if ("retrive".equals(what) && body.has("key") && body.has("callBack")) {
					JSONObject val = nsr.getJSONData(body.getString("key"));
					eval(body.getString("callBack") + "(" + (val != null ? val.toString() : "null") + ")");
				}
				if ("callApi".equals(what) && body.has("callBack")) {
					nsr.authorize(new NSRAuth() {
						public void authorized(boolean authorized) throws Exception {
							if (!authorized) {
								JSONObject result = new JSONObject();
								result.put("status", "error");
								result.put("message", "not authorized");
								eval(body.getString("callBack") + "(" + result.toString() + ")");
								return;
							}
							JSONObject headers = new JSONObject();
							headers.put("ns_token", nsr.getToken());
							headers.put("ns_lang", nsr.getLang());
							nsr.getSecurityDelegate().secureRequest(ctx, body.getString("endpoint"), body.has("payload") ? body.getJSONObject("payload") : null, headers, new NSRSecurityResponse() {
								public void completionHandler(JSONObject json, String error) throws Exception {
									if (error == null) {
										eval(body.getString("callBack") + "(" + json.toString() + ")");
									} else {
										NSRLog.e(NSR.TAG, "secureRequest: " + error);
										JSONObject result = new JSONObject();
										result.put("status", "error");
										result.put("message", error);
										eval(body.getString("callBack") + "(" + result.toString() + ")");
									}
								}
							});
						}
					});
				}
				if ("accurateLocation".equals(what) && body.has("meters") && body.has("duration")) {
					boolean extend = nsr.getBoolean(body, "extend");
					nsr.accurateLocation(body.getDouble("meters"), body.getInt("duration"), extend);
				}
				if ("accurateLocationEnd".equals(what)) {
					nsr.accurateLocationEnd();
				}
			}
		} catch (Exception e) {
			NSRLog.e(NSR.TAG, "postMessage", e);
		}
	}

	public synchronized void finish() {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			public void run() {
				try {
					if (webView != null) {
						webView.stopLoading();
						webView.destroy();
						webView = null;
					}
				} catch (Throwable e) {
				}
			}
		});
	}
}
