package eu.neosurance.sdk;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NSR {
	protected String getVersion() {
		return "2.1.7";
	}

	protected String getOs() {
		return "Android";
	}

	protected static final String PREFS_NAME = "NSRSDK";
	protected static final String TAG = "nsr";
	protected static final int PERMISSIONS_MULTIPLE_ACCESSLOCATION = 0x2043;
	protected static final int PERMISSIONS_MULTIPLE_IMAGECAPTURE = 0x2049;
	protected static final int REQUEST_IMAGE_CAPTURE = 0x1702;

	private static NSR instance = null;
	private Context ctx = null;
	private NSREventWebView eventWebView = null;
	private long eventWebViewSynchTime = 0;

	private NSRSecurityDelegate securityDelegate = null;
	private NSRWorkflowDelegate workflowDelegate = null;
	private NSRPushDelegate pushDelegate = null;

	private NSRActivityWebView activityWebView = null;

	private FusedLocationProviderClient locationClient = null;
	private PendingIntent locationIntent = null;

	private ActivityRecognitionClient activityClient = null;
	private PendingIntent activityIntent = null;
	private boolean stillLocationSent = false;

	private NSR(Context ctx) {
		this.ctx = ctx;
	}

	protected static boolean getBoolean(JSONObject obj, String key) {
		try {
			return (obj.get(key) instanceof Boolean) ? obj.getBoolean(key) : (obj.getInt(key) != 0);
		} catch (Exception e) {
			return false;
		}
	}

	protected static boolean gracefulDegradate() {
		return Build.VERSION.SDK_INT < 21;
	}

	public static NSR getInstance(Context ctx) {
		if (instance == null) {
			Log.d(TAG, "making instance...");
			instance = new NSR(ctx);
			if (!gracefulDegradate()) {
				try {
					String s = instance.getData("securityDelegateClass");
					if (s != null) {
						Log.d(TAG, "making securityDelegate... " + s);
						instance.setSecurityDelegate((NSRSecurityDelegate) Class.forName(s).newInstance());
					} else {
						Log.d(TAG, "making securityDelegate... NSRDefaultSecurity");
						instance.setSecurityDelegate(new NSRDefaultSecurity());
					}

					s = instance.getData("workflowDelegateClass");
					if (s != null) {
						Log.d(TAG, "making workflowDelegate... " + s);
						instance.setWorkflowDelegate((NSRWorkflowDelegate) Class.forName(s).newInstance());
					}

					s = instance.getData("pushDelegateClass");
					if (s != null) {
						Log.d(TAG, "making pushDelegateClass... " + s);
						instance.setPushDelegate((NSRPushDelegate) Class.forName(s).newInstance());
					}

					instance.initJob();
				} catch (Exception e) {
					Log.e(TAG, "getInstance", e);
				}
			}
		} else {
			instance.ctx = ctx;
		}
		return instance;
	}

	private void initJob() {
		Log.d(TAG, "initJob");
		try {
			stopTraceLocation();
			stopTraceActivity();
			JSONObject conf = getConf();
			if (conf != null && eventWebView == null && getBoolean(conf, "local_tracking")) {
				Log.d(TAG, "Making NSREventWebView");
				eventWebView = new NSREventWebView(ctx, this);
			}
			traceActivity();
			traceLocation();
		} catch (Exception e) {
			Log.e(TAG, "initJob", e);
		}
	}

	private void synchEventWebView() {
		long t = System.currentTimeMillis() / 1000;
		if (eventWebView != null && t - eventWebViewSynchTime > (60 * 60 * 8)) {
			eventWebView.synch();
		}
	}

	public void resetCruncher() {
		eventWebViewSynchTime = 0;
		if (eventWebView != null) {
			eventWebView.reset();
		}
	}

	protected void eventWebViewSynched() {
		eventWebViewSynchTime = System.currentTimeMillis() / 1000;
	}

	private boolean needsInitJob(JSONObject conf, JSONObject oldConf) throws Exception {
		return (oldConf == null) || (conf.getInt("time") != oldConf.getInt("time")) || (eventWebView == null && getBoolean(conf, "local_tracking"));
	}

	private synchronized void initLocation() {
		if (locationClient == null) {
			Log.d(TAG, "initLocation");
			locationClient = LocationServices.getFusedLocationProviderClient(ctx);
		}
	}

	protected void traceLocation() {
		Log.d(TAG, "traceLocation");
		try {
			boolean fine = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			boolean coarse = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			if (coarse || fine) {
				JSONObject conf = getConf();
				if (conf != null && getBoolean(conf.getJSONObject("position"), "enabled")) {
					initLocation();
					long time = conf.getLong("time") * 1000;
					float meters = (float) conf.getJSONObject("position").getDouble("meters");
					LocationRequest locationRequest = LocationRequest.create();
					locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
					locationRequest.setFastestInterval(time / 3);
					locationRequest.setInterval(time);
					locationRequest.setSmallestDisplacement(meters);
					Log.d(TAG, "requestLocationUpdates");
					locationIntent = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, NSRLocationCallback.class), PendingIntent.FLAG_UPDATE_CURRENT);
					locationClient.requestLocationUpdates(locationRequest, locationIntent);
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, "traceLocation", e);
		}
	}

	protected synchronized void stopTraceLocation() {
		if (locationClient != null && locationIntent != null) {
			Log.d(TAG, "stopTraceLocation");
			locationClient.removeLocationUpdates(locationIntent);
			locationIntent = null;
		}
	}

	protected Location getLastLocation() {
		try {
			JSONObject loc = getJSONData("lastLocation");
			if (loc != null) {
				Location lastLocation = new Location("");
				lastLocation.setLatitude(loc.getDouble("latitude"));
				lastLocation.setLongitude(loc.getDouble("longitude"));
				lastLocation.setAltitude(loc.getDouble("altitude"));
				return lastLocation;
			} else {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	protected void setLastLocation(Location lastLocation) {
		if (lastLocation != null) {
			try {
				JSONObject loc = new JSONObject();
				loc.put("latitude", lastLocation.getLatitude());
				loc.put("longitude", lastLocation.getLongitude());
				loc.put("altitude", lastLocation.getAltitude());
				setJSONData("lastLocation", loc);
			} catch (Exception e) {
				setJSONData("lastLocation", null);
			}
		} else {
			setJSONData("lastLocation", null);
		}
	}

	protected boolean getStillLocationSent() {
		return stillLocationSent;
	}

	protected void setStillLocationSent(boolean stillLocationSent) {
		this.stillLocationSent = stillLocationSent;
	}

	private synchronized void initActivity() {
		if (activityClient == null) {
			Log.d(TAG, "initActivity");
			activityClient = ActivityRecognition.getClient(ctx);
		}
	}

	protected synchronized void traceActivity() {
		Log.d(TAG, "traceActivity");
		try {
			JSONObject conf = getConf();
			if (conf != null && getBoolean(conf.getJSONObject("activity"), "enabled")) {
				initActivity();
				long time = conf.getLong("time") * 1000;
				Log.d(TAG, "requestActivityUpdates");
				activityIntent = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, NSRActivityCallback.class), PendingIntent.FLAG_UPDATE_CURRENT);
				activityClient.requestActivityUpdates(time, activityIntent);
			}
		} catch (JSONException e) {
			Log.e(TAG, "traceActivity", e);
		}
	}

	protected synchronized void stopTraceActivity() {
		if (activityClient != null && activityIntent != null) {
			Log.d(TAG, "stopTraceActivity");
			activityClient.removeActivityUpdates(activityIntent);
			activityIntent = null;
		}
	}

	protected String getLastActivity() {
		return getData("lastActivity");
	}

	protected void setLastActivity(String lastActivity) {
		setData("lastActivity", lastActivity);
	}

	protected void tracePower() {
		Log.d(TAG, "tracePower");
		try {
			JSONObject conf = getConf();
			if (conf != null && getBoolean(conf.getJSONObject("power"), "enabled")) {
				Intent batteryStatus = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
				int powerLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				String power = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0 ? "plugged" : "unplugged";
				if (!power.equals(getLastPower()) || Math.abs(powerLevel - getLastPowerLevel()) >= 5) {
					JSONObject payload = new JSONObject();
					payload.put("type", power);
					payload.put("level", powerLevel);
					crunchEvent("power", payload);
					setLastPower(power);
					setLastPowerLevel(powerLevel);
				}
			}
		} catch (Exception e) {
			Log.e(NSR.TAG, "tracePower", e);
		}
	}

	protected String getLastPower() {
		return getData("lastPower");
	}

	protected void setLastPower(String lastPower) {
		setData("lastPower", lastPower);
	}

	protected int getLastPowerLevel() {
		try {
			String s = getData("lastPowerLevel");
			return s != null ? Integer.parseInt(s) : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	protected void setLastPowerLevel(int lastPower) {
		setData("lastPowerLevel", "" + lastPower);
	}

	protected void traceConnection() {
		Log.d(TAG, "traceConnection");
		try {
			JSONObject conf = getConf();
			if (conf != null && getBoolean(conf.getJSONObject("connection"), "enabled")) {
				String connection = null;
				NetworkInfo info = ((ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
				if (info != null && info.isConnected()) {
					switch (info.getType()) {
						case ConnectivityManager.TYPE_WIFI:
							connection = "wi-fi";
							break;
						case ConnectivityManager.TYPE_MOBILE:
							connection = "mobile";
					}
				}
				if (connection != null && !connection.equals(getLastConnection())) {
					JSONObject payload = new JSONObject();
					payload.put("type", connection);
					crunchEvent("connection", payload);
					setLastConnection(connection);
				}
			}
		} catch (Exception e) {
			Log.e(NSR.TAG, "traceConnection", e);
		}
	}

	protected String getLastConnection() {
		return getData("lastConnection");
	}

	protected void setLastConnection(String lastConnection) {
		setData("lastConnection", lastConnection);
	}

	protected void opportunisticTrace() {
		tracePower();
		traceConnection();
		try {
			String locationAuth = "notAuthorized";
			boolean fine = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			boolean coarse = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			if (coarse && fine)
				locationAuth = "authorized";
			else if (fine)
				locationAuth = "fine";
			else if (coarse)
				locationAuth = "coarse";
			String lastLocationAuth = getLastLocationAuth();
			if (!locationAuth.equals(lastLocationAuth)) {
				setLastLocationAuth(locationAuth);
				JSONObject payload = new JSONObject();
				payload.put("status", locationAuth);
				crunchEvent("locationAuth", payload);
			}

			String pushAuth = (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) ? "authorized" : "notAuthorized";
			String lastPushAuth = getLastPushAuth();
			if (!pushAuth.equals(lastPushAuth)) {
				setLastPushAuth(pushAuth);
				JSONObject payload = new JSONObject();
				payload.put("status", pushAuth);
				crunchEvent("pushAuth", payload);
			}
		} catch (Exception e) {
		}
	}

	protected String getLastLocationAuth() {
		return getData("locationAuth");
	}

	protected void setLastLocationAuth(String locationAuth) {
		setData("locationAuth", locationAuth);
	}

	protected String getLastPushAuth() {
		return getData("pushAuth");
	}

	protected void setLastPushAuth(String pushAuth) {
		setData("pushAuth", pushAuth);
	}


	protected void registerWebView(NSRActivityWebView activityWebView) {
		if (this.activityWebView != null)
			this.activityWebView.finish();
		this.activityWebView = activityWebView;
	}

	protected void clearWebView() {
		this.activityWebView = null;
	}

	protected NSRSecurityDelegate getSecurityDelegate() {
		return securityDelegate;
	}

	public void setSecurityDelegate(NSRSecurityDelegate securityDelegate) {
		if (gracefulDegradate()) {
			return;
		}
		setData("securityDelegateClass", securityDelegate.getClass().getName());
		this.securityDelegate = securityDelegate;
	}

	protected NSRWorkflowDelegate getWorkflowDelegate() {
		return workflowDelegate;
	}

	public void setWorkflowDelegate(NSRWorkflowDelegate workflowDelegate) {
		if (gracefulDegradate()) {
			return;
		}
		setData("workflowDelegateClass", workflowDelegate.getClass().getName());
		this.workflowDelegate = workflowDelegate;
	}

	protected NSRPushDelegate getPushDelegate() {
		return pushDelegate;
	}

	public void setPushDelegate(NSRPushDelegate pushDelegate) {
		if (gracefulDegradate()) {
			return;
		}
		setData("pushDelegateClass", pushDelegate.getClass().getName());
		this.pushDelegate = pushDelegate;
	}


	public void setup(final JSONObject settings) {
		if (gracefulDegradate()) {
			return;
		}
		Log.d(TAG, "setup");
		try {
			if (!settings.has("ns_lang")) {
				settings.put("ns_lang", Locale.getDefault().getLanguage());
			}
			if (!settings.has("push_icon")) {
				settings.put("push_icon", R.drawable.nsr_logo);
			}
			Log.d(TAG, "setup: " + settings);
			setSettings(settings);
			if (getData("permission_requested") == null && getBoolean(settings, "ask_permission")) {
				setData("permission_requested", "*");
				List<String> permissionsList = new ArrayList<String>();
				if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					permissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
				}
				if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					permissionsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
				}
				if (permissionsList.size() > 0) {
					ActivityCompat.requestPermissions((Activity) ctx, permissionsList.toArray(new String[permissionsList.size()]), NSR.PERMISSIONS_MULTIPLE_ACCESSLOCATION);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "setup", e);
		}
	}

	public void registerUser(NSRUser user) {
		if (gracefulDegradate()) {
			return;
		}
		Log.d(TAG, "registerUser");
		try {
			forgetUser();
			setUser(user);
			authorize(new NSRAuth() {
				public void authorized(boolean authorized) throws Exception {
					Log.d(TAG, "registerUser: " + (authorized ? "" : "not ") + "authorized!");
					if (authorized && getBoolean(getConf(), "send_user")) {
						Log.d(TAG, "sendUser");
						try {
							JSONObject devicePayLoad = new JSONObject();
							devicePayLoad.put("uid", getDeviceUid());
							String pushToken = getPushToken();
							if (pushToken != null) {
								devicePayLoad.put("push_token", pushToken);
							}
							devicePayLoad.put("os", getOs());
							devicePayLoad.put("version", "[sdk:" + getVersion() + "] " + Build.VERSION.RELEASE + " " + Build.VERSION_CODES.class.getFields()[Build.VERSION.SDK_INT].getName());
							devicePayLoad.put("model", Build.MODEL);

							JSONObject requestPayload = new JSONObject();
							requestPayload.put("user", getUser().toJsonObject(false));
							requestPayload.put("device", devicePayLoad);

							JSONObject headers = new JSONObject();
							String token = getToken();
							Log.d(TAG, "sendUser token: " + token);
							headers.put("ns_token", token);
							headers.put("ns_lang", getLang());

							Log.d(TAG, "requestPayload: " + requestPayload.toString());

							getSecurityDelegate().secureRequest(ctx, "register", requestPayload, headers, new NSRSecurityResponse() {
								public void completionHandler(JSONObject json, String error) throws Exception {
									if (error != null) {
										Log.e(TAG, "sendUser secureRequest: " + error);
									}
								}
							});
						} catch (Exception e) {
							Log.e(TAG, "sendUser", e);
						}
					}
				}
			});
		} catch (Exception e) {
			Log.e(TAG, "registerUser", e);
		}
	}

	public void forgetUser() {
		if (gracefulDegradate()) {
			return;
		}
		Log.d(TAG, "forgetUser");
		setConf(null);
		setAuth(null);
		setAppURL(null);
		setUser(null);
		initJob();
	}

	protected void authorize(final NSRAuth delegate) throws Exception {
		Log.d(TAG, "authorize");
		JSONObject auth = getAuth();
		if (auth != null && (auth.getLong("expire") - System.currentTimeMillis()) > 0) {
			delegate.authorized(true);
		} else {
			NSRUser user = getUser();
			JSONObject settings = getSettings();
			if (user != null && settings != null) {
				try {
					JSONObject payload = new JSONObject();
					payload.put("user_code", user.getCode());
					payload.put("code", settings.getString("code"));
					payload.put("secret_key", settings.getString("secret_key"));

					JSONObject sdkPayload = new JSONObject();
					sdkPayload.put("version", getVersion());
					sdkPayload.put("dev", getBoolean(settings, "dev_mode"));
					sdkPayload.put("os", getOs());
					payload.put("sdk", sdkPayload);

					securityDelegate.secureRequest(this.ctx, "authorize", payload, null, new NSRSecurityResponse() {
						public void completionHandler(JSONObject response, String error) throws Exception {
							if (error == null) {
								JSONObject auth = response.getJSONObject("auth");
								Log.d(TAG, "authorize auth: " + auth);
								setAuth(auth);

								JSONObject oldConf = getConf();
								JSONObject conf = response.getJSONObject("conf");
								Log.d(TAG, "authorize conf: " + conf);
								setConf(conf);

								String appUrl = response.getString("app_url");
								Log.d(TAG, "authorize appUrl: " + appUrl);
								setAppURL(appUrl);

								if (needsInitJob(conf, oldConf)) {
									Log.d(TAG, "authorize needsInitJob");
									initJob();
								}
								if (getBoolean(conf, "local_tracking")) {
									synchEventWebView();
								}
								delegate.authorized(true);
							} else {
								delegate.authorized(false);
							}
						}
					});
				} catch (Exception e) {
					Log.e(TAG, "authorize", e);
					delegate.authorized(false);
				}
			}
		}
	}

	public void sendAction(final String name, final String policyCode, final String details) {
		if (gracefulDegradate()) {
			return;
		}
		Log.d(TAG, "sendAction - name: " + name + " policyCode: " + policyCode + " details: " + details);
		try {
			authorize(new NSRAuth() {
				public void authorized(boolean authorized) throws Exception {
					JSONObject requestPayload = new JSONObject();

					requestPayload.put("action", name);
					requestPayload.put("code", policyCode);
					requestPayload.put("details", details);
					requestPayload.put("timezone", TimeZone.getDefault().getID());
					requestPayload.put("action_time", System.currentTimeMillis());

					JSONObject headers = new JSONObject();
					String token = getToken();
					Log.d(TAG, "sendAction token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					getSecurityDelegate().secureRequest(ctx, "trace", requestPayload, headers, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error != null) {
								Log.e(TAG, "sendAction: " + error);
							} else {
								Log.d(TAG, "sendAction: " + json.toString());
							}
						}
					});
				}
			});
		} catch (Exception e) {
			Log.e(TAG, "sendAction", e);
		}
	}

	public void crunchEvent(final String event, final JSONObject payload) throws Exception {
		JSONObject conf = getConf();
		if (getBoolean(conf, "local_tracking")) {
			Log.d(NSR.TAG, "crunchEvent: " + event + " payload: " + payload.toString());
			snapshot(event, payload);
			if (eventWebView != null) {
				eventWebView.crunchEvent(event, payload);
			}
		} else {
			sendEvent(event, payload);
		}
	}

	public void sendEvent(final String event, final JSONObject payload) {
		if (gracefulDegradate()) {
			return;
		}
		Log.d(TAG, "sendEvent - event: " + event + " payload: " + payload);
		try {
			authorize(new NSRAuth() {
				public void authorized(boolean authorized) throws Exception {
					if (!authorized) {
						return;
					}
					snapshot(event, payload);
					JSONObject eventPayLoad = new JSONObject();
					eventPayLoad.put("event", event);
					eventPayLoad.put("timezone", TimeZone.getDefault().getID());
					eventPayLoad.put("event_time", System.currentTimeMillis());
					eventPayLoad.put("payload", payload);

					JSONObject devicePayLoad = new JSONObject();
					devicePayLoad.put("uid", getDeviceUid());
					String pushToken = getPushToken();
					if (pushToken != null) {
						devicePayLoad.put("push_token", pushToken);
					}
					devicePayLoad.put("os", getOs());
					devicePayLoad.put("version", "[sdk:" + getVersion() + "] " + Build.VERSION.RELEASE + " " + Build.VERSION_CODES.class.getFields()[Build.VERSION.SDK_INT].getName());
					devicePayLoad.put("model", Build.MODEL);

					JSONObject requestPayload = new JSONObject();
					requestPayload.put("event", eventPayLoad);
					requestPayload.put("user", getUser().toJsonObject(false));
					requestPayload.put("device", devicePayLoad);
					if (getBoolean(getConf(), "send_snapshot")) {
						requestPayload.put("snapshot", snapshot());
					}

					JSONObject headers = new JSONObject();
					String token = getToken();
					Log.d(TAG, "sendEvent token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					Log.d(TAG, "requestPayload: " + requestPayload.toString());

					getSecurityDelegate().secureRequest(ctx, "event", requestPayload, headers, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error == null) {
								if (json.has("pushes")) {
									boolean skipPush = !json.has("skipPush") || getBoolean(json, "skipPush");
									JSONArray pushes = json.getJSONArray("pushes");
									if (!skipPush) {
										if (pushes.length() > 0) {
											JSONObject push = pushes.getJSONObject(0);
											String imageUrl = push.has("imageUrl") ? push.getString("imageUrl") : null;
											String url = push.has("url") ? push.getString("url") : null;
											PendingIntent pendingIntent = null;
											if (url != null && !"".equals(url)) {
												if (getPushDelegate() != null) {
													pendingIntent = getPushDelegate().makePendingIntent(ctx, push);
												} else {
													pendingIntent = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), makeActivityWebView(url), PendingIntent.FLAG_UPDATE_CURRENT);
												}
											}
											NSRNotification.sendNotification(ctx, push.getString("title"), push.getString("body"), imageUrl, pendingIntent);
										}
									} else {
										if (pushes.length() > 0) {
											JSONObject notification = pushes.getJSONObject(0);
											Log.d(TAG, notification.toString());
											showUrl(notification.getString("url"));
										}
									}
								}
							} else {
								Log.e(TAG, "sendEvent secureRequest: " + error);
							}
						}
					});
				}
			});
		} catch (Exception e) {
			Log.e(TAG, "sendEvent", e);
		}
	}

	public void archiveEvent(final String event, final JSONObject payload) {
		if (gracefulDegradate()) {
			return;
		}
		Log.d(TAG, "archiveEvent - event: " + event + " payload: " + payload);
		try {
			authorize(new NSRAuth() {
				public void authorized(boolean authorized) throws Exception {
					if (!authorized) {
						return;
					}
					JSONObject eventPayLoad = new JSONObject();
					eventPayLoad.put("event", event);
					eventPayLoad.put("timezone", TimeZone.getDefault().getID());
					eventPayLoad.put("event_time", System.currentTimeMillis());
					eventPayLoad.put("payload", new JSONObject());

					JSONObject devicePayLoad = new JSONObject();
					devicePayLoad.put("uid", getDeviceUid());

					JSONObject userPayLoad = new JSONObject();
					userPayLoad.put("code", getUser().getCode());

					JSONObject requestPayload = new JSONObject();
					requestPayload.put("event", eventPayLoad);
					requestPayload.put("user", userPayLoad);
					requestPayload.put("device", devicePayLoad);
					requestPayload.put("snapshot", snapshot(event, payload));

					JSONObject headers = new JSONObject();
					String token = getToken();
					Log.d(TAG, "archiveEvent token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					Log.d(TAG, "requestPayload: " + requestPayload.toString());

					getSecurityDelegate().secureRequest(ctx, "archiveEvent", requestPayload, headers, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error != null) {
								Log.e(TAG, "archiveEvent secureRequest: " + error);
							}
						}
					});
				}
			});
		} catch (Exception e) {
			Log.e(TAG, "archiveEvent", e);
		}
	}

	public void showApp() {
		if (getAppURL() != null) {
			showUrl(getAppURL(), null);
		}
	}

	public void showApp(JSONObject params) {
		if (getAppURL() != null) {
			showUrl(getAppURL(), params);
		}
	}

	public void showUrl(String url) {
		showUrl(url, null);
	}

	public synchronized void showUrl(String url, JSONObject params) {
		if (gracefulDegradate()) {
			return;
		}
		try {
			if (params != null && params.length() > 0) {
				Iterator<String> keys = params.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					url += ((url.indexOf('?') < 0) ? "?" : "&") + key + "=" + URLEncoder.encode(params.getString(key), "UTF-8");
				}
			}
			if (activityWebView != null) {
				activityWebView.navigate(url);
			} else {
				ctx.startActivity(makeActivityWebView(url));
			}
		} catch (Exception e) {
			Log.e(TAG, "showUrl", e);
		}
	}

	protected Intent makeActivityWebView(String url) throws Exception {
		Intent intent = new Intent(ctx, NSRActivityWebView.class);
		intent.putExtra("url", url);
		return intent;
	}

	protected NSRUser getUser() {
		try {
			JSONObject user = getJSONData("user");
			return user != null ? new NSRUser(user) : null;
		} catch (Exception e) {
			Log.e(TAG, "getUser", e);
			return null;
		}
	}

	protected void setUser(NSRUser user) {
		setJSONData("user", user == null ? null : user.toJsonObject(true));
	}

	protected JSONObject getConf() {
		return getJSONData("conf");
	}

	protected void setConf(JSONObject conf) {
		setJSONData("conf", conf);
	}

	protected JSONObject getSettings() {
		return getJSONData("settings");
	}

	protected void setSettings(JSONObject settings) {
		setJSONData("settings", settings);
	}

	protected JSONObject getAuth() {
		return getJSONData("auth");
	}

	protected void setAuth(JSONObject auth) {
		setJSONData("auth", auth);
	}

	protected String getToken() {
		try {
			return getAuth().has("token") ? getAuth().getString("token") : null;
		} catch (Exception e) {
			Log.e(TAG, "getToken", e);
			return null;
		}
	}

	protected String getPushToken() {
		try {
			return getSettings().has("push_token") ? getSettings().getString("push_token") : null;
		} catch (Exception e) {
			Log.e(TAG, "getPushToken", e);
			return null;
		}
	}

	protected String getLang() {
		try {
			return getSettings().has("ns_lang") ? getSettings().getString("ns_lang") : null;
		} catch (Exception e) {
			Log.e(TAG, "getLang", e);
			return null;
		}
	}

	protected String getAppURL() {
		return getData("appURL");
	}

	protected void setAppURL(String appURL) {
		setData("appURL", appURL);
	}

	protected synchronized JSONObject snapshot(final String event, final JSONObject payload) {
		JSONObject snapshot = snapshot();
		try {
			snapshot.put(event, payload);
		} catch (Exception e) {
		}
		setJSONData("snapshot", snapshot);
		return snapshot;
	}

	protected synchronized JSONObject snapshot() {
		JSONObject snapshot = getJSONData("snapshot");
		if (snapshot == null) {
			snapshot = new JSONObject();
		}
		return snapshot;
	}


	protected String getData(String key) {
		if (getSharedPreferences().contains(key)) {
			return getSharedPreferences().getString(key, "");
		} else {
			return null;
		}
	}

	protected void setData(String key, String value) {
		SharedPreferences.Editor editor = getSharedPreferences().edit();
		if (value != null) {
			editor.putString(key, value);
		} else {
			editor.remove(key);
		}
		editor.commit();
	}

	protected JSONObject getJSONData(String key) {
		try {
			if (getSharedPreferences().contains(key))
				return new JSONObject(getSharedPreferences().getString(key, "{}"));
			else
				return null;
		} catch (JSONException e) {
			return null;
		}
	}

	protected void setJSONData(String key, JSONObject value) {
		SharedPreferences.Editor editor = getSharedPreferences().edit();
		if (value != null) {
			editor.putString(key, value.toString());
		} else {
			editor.remove(key);
		}
		editor.commit();
	}

	protected SharedPreferences getSharedPreferences() {
		return ctx.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE);
	}

	protected static Date jsonStringToDate(String s) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			return sdf.parse(s);
		} catch (Exception e) {
			return null;
		}
	}

	protected static String dateToJsonString(Date date) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			return sdf.format(date);
		} catch (Exception e) {
			return null;
		}
	}

	protected String getDeviceUid() {
		return Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
	}

	public void loginExecuted(String url) {
		if (gracefulDegradate()) {
			return;
		}
		try {
			JSONObject params = new JSONObject();
			params.put("loginExecuted", "yes");
			showUrl(url, params);
		} catch (Exception e) {
			Log.e(TAG, "loginExecuted", e);
		}
	}

	public void paymentExecuted(JSONObject paymentInfo, String url) {
		if (gracefulDegradate()) {
			return;
		}
		try {
			JSONObject params = new JSONObject();
			params.put("paymentExecuted", paymentInfo.toString());
			showUrl(url, params);
		} catch (Exception e) {
			Log.e(TAG, "paymentExecuted", e);
		}
	}
}
