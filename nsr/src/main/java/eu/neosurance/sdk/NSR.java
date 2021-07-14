package eu.neosurance.sdk;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Base64;

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
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class NSR {
	protected String getVersion() {
		return "2.3.3";
	}

	protected String getOs() {
		return "Android";
	}

	private final static byte[] K = Base64.decode("Ux44AGRuanL0y7qQDeasT3", Base64.NO_WRAP);
	private final static byte[] I = Base64.decode("ycB4AGR7a0fhoFXbpoHy43", Base64.NO_WRAP);

	private static final String CHANNEL_ID = "NSRNotification";
	protected static final String PREFS_NAME = "NSRSDK";
	protected static final String TAG = "nsr";
	protected static final int PERMISSIONS_MULTIPLE_ACCESSLOCATION = 0x2043;
	protected static final int PERMISSIONS_MULTIPLE_IMAGECAPTURE = 0x2049;
	protected static final int REQUEST_IMAGE_CAPTURE = 0x1702;

	private static NSR instance = null;
	protected Context ctx = null;
	private NSREventWebView eventWebView = null;

	private NSRSecurityDelegate securityDelegate = null;
	private NSRWorkflowDelegate workflowDelegate = null;
	private NSRPushDelegate pushDelegate = null;

	private NSRActivityWebView activityWebView = null;

	private FusedLocationProviderClient hardLocationClient = null;
	private NSRLocationCallback hardLocationCallback = null;
	private Intent foregrounder = null;

	private ActivityRecognitionClient activityClient = null;
	private PendingIntent activityIntent = null;

	private String bckLoc = null;

	public String getBckLoc() {
		return bckLoc;
	}

	protected void setBckLoc(String bckLoc) {
		this.bckLoc = bckLoc;
	}

	protected NSR(Context ctx) {
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
			NSRLog.d(TAG, "making instance...");
			instance = new NSR(ctx);
			if(ctx == null)
				return instance;
			if (!gracefulDegradate()) {
				try {
					String s = instance.getData("securityDelegateClass");
					if (s != null) {
						NSRLog.d(TAG, "making securityDelegate... " + s);
						instance.setSecurityDelegate((NSRSecurityDelegate) Class.forName(s).newInstance());
					} else {
						NSRLog.d(TAG, "making securityDelegate... NSRDefaultSecurity");
						instance.setSecurityDelegate(new NSRDefaultSecurity());
					}
					s = instance.getData("workflowDelegateClass");
					if (s != null) {
						NSRLog.d(TAG, "making workflowDelegate... " + s);
						instance.setWorkflowDelegate((NSRWorkflowDelegate) Class.forName(s).newInstance());
					}
					s = instance.getData("pushDelegateClass");
					if (s != null) {
						NSRLog.d(TAG, "making pushDelegateClass... " + s);
						instance.setPushDelegate((NSRPushDelegate) Class.forName(s).newInstance());
					}
					instance.initJob();
				} catch (Exception e) {
					NSRLog.e(TAG, "getInstance", e);
					NSRLog.d(TAG, "makePristine....");
					SharedPreferences.Editor editor = instance.getSharedPreferences().edit();
					editor.remove("securityDelegateClass");
					editor.remove("workflowDelegateClass");
					editor.remove("pushDelegateClass");
					editor.remove("conf");
					editor.remove("settings");
					editor.remove("user");
					editor.remove("auth");
					editor.remove("appURL");
					editor.commit();
					instance.stopHardTraceLocation();
					instance.stopTraceActivity();
					NSRLog.d(TAG, "pristine!");
					instance.setSecurityDelegate(new NSRDefaultSecurity());
				}
			}
		} else {
			instance.ctx = ctx;
		}
		return instance;
	}

	protected void initBck(int delay) {
		if (Build.VERSION.SDK_INT >= 21) {
			NSRLog.d(TAG, "initBck....");
			PendingIntent pendingIntent = PendingIntent.getBroadcast(ctx, 1, new Intent(ctx, NSRBck.class), PendingIntent.FLAG_UPDATE_CURRENT);
			((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay * 1000, pendingIntent);
			NSRLog.d(TAG, "initBck in " + delay);
		}
	}

	private void initJob() {
		NSRLog.d(TAG, "initJob");
		try {
			stopHardTraceLocation();
			stopTraceActivity();
			if (!synchEventWebView()) {
				continueInitJob();
			}
		} catch (Exception e) {
			NSRLog.e(TAG, "initJob", e);
		}
	}

	protected void continueInitJob() {
		traceActivity();
		traceLocation();
		hardTraceLocation();
	}

	private boolean synchEventWebView() {
		if (getBoolean(getConf(), "local_tracking")) {
			if (eventWebView == null) {
				NSRLog.d(TAG, "Making NSREventWebView");
				eventWebView = new NSREventWebView(ctx, this);
				return true;
			} else {
				eventWebView.synch();
			}
		} else if (eventWebView != null) {
			eventWebView.finish();
			eventWebView = null;
		}
		return false;
	}

	public void resetCruncher() {
		if (eventWebView != null) {
			eventWebView.reset();
		}
	}

	private boolean needsInitJob(JSONObject conf, JSONObject oldConf) throws Exception {
		return (oldConf == null) || !oldConf.toString().equals(conf.toString()) || (eventWebView == null && getBoolean(conf, "local_tracking"));
	}

	private synchronized void initHardLocation() {
		if (hardLocationClient == null) {
			NSRLog.d(TAG, "initHardLocation");
			hardLocationClient = LocationServices.getFusedLocationProviderClient(ctx);
		}
	}

	protected void traceLocation() {
		NSRLog.d(TAG, "traceLocation");
		try {
			JSONObject conf = getConf();
			boolean fine = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			boolean coarse = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			if ((coarse || fine) && conf != null && getBoolean(conf.getJSONObject("position"), "enabled")) {
				initBck(3);
			}
		} catch (JSONException e) {
			NSRLog.e(TAG, "traceLocation", e);
		}
	}

	protected void hardTraceLocation() {
		NSRLog.d(TAG, "hardTraceLocation");
		try {
			JSONObject conf = getConf();
			boolean fine = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			boolean coarse = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			if (coarse || fine && conf != null && getBoolean(conf.getJSONObject("position"), "enabled")) {
				if (isHardTraceLocation()) {
					initHardLocation();
					hardLocationCallback = new NSRLocationCallback(this, null);
					hardLocationClient.requestLocationUpdates(makeHardLocationRequest(getHardTraceMeters()), hardLocationCallback, null);
					NSRLog.d(TAG, "hardTraceLocation reactivated");
				}
			}
		} catch (JSONException e) {
			NSRLog.e(TAG, "hardTraceLocation", e);
		}
	}

	private LocationRequest makeHardLocationRequest(double meters) {
		if (Build.VERSION.SDK_INT >= 26 && foregrounder == null) {
			foregrounder = new Intent(ctx, NSRForeground.class);
			ctx.startForegroundService(foregrounder);
		}
		LocationRequest locationRequest = LocationRequest.create();
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationRequest.setFastestInterval(0);
		locationRequest.setInterval(0);
		locationRequest.setSmallestDisplacement((float) meters);
		return locationRequest;
	}

	protected synchronized void stopHardTraceLocation() {
		if (hardLocationClient != null && hardLocationCallback != null) {
			NSRLog.d(TAG, "stopHardTraceLocation");
			if (Build.VERSION.SDK_INT >= 26 && foregrounder != null) {
				ctx.stopService(foregrounder);
				foregrounder = null;
			}
			hardLocationClient.removeLocationUpdates(hardLocationCallback);
			hardLocationCallback = null;
		}
	}

	public void accurateLocation(double meters, int duration, boolean extend) {
		NSRLog.i(TAG, "accurateLocation >>>>");
		try {
			JSONObject conf = getConf();
			boolean fine = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			boolean coarse = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			if ((coarse || fine) && conf != null && getBoolean(conf.getJSONObject("position"), "enabled")) {
				NSRLog.i(TAG, "accurateLocation");
				initHardLocation();
				if (!isHardTraceLocation() || meters != getHardTraceMeters()) {
					stopHardTraceLocation();
					setHardTraceMeters(meters);
					setHardTraceEnd((int) (System.currentTimeMillis() / 1000) + duration);
					hardLocationCallback = new NSRLocationCallback(this, null);
					hardLocationClient.requestLocationUpdates(makeHardLocationRequest((float) meters), hardLocationCallback, null);
				}
				if (extend) {
					setHardTraceEnd((int) (System.currentTimeMillis() / 1000) + duration);
				}
			}
		} catch (JSONException e) {
			NSRLog.e(TAG, "accurateLocation", e);
		}
	}

	public void accurateLocationEnd() {
		NSRLog.i(TAG, "accurateLocationEnd");
		stopHardTraceLocation();
		setHardTraceEnd(0);
	}

	protected void checkHardTraceLocation() {
		if (!isHardTraceLocation()) {
			stopHardTraceLocation();
			setHardTraceEnd(0);
		}
	}

	protected boolean isHardTraceLocation() {
		int hte = getHardTraceEnd();
		return (hte > 0 && (System.currentTimeMillis() / 1000) < hte);
	}

	protected int getHardTraceEnd() {
		try {
			String s = getData("hardTraceEnd");
			return s != null ? Integer.parseInt(s) : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	protected void setHardTraceEnd(int hardTraceEnd) {
		setData("hardTraceEnd", "" + hardTraceEnd);
	}

	protected double getHardTraceMeters() {
		try {
			String s = getData("hardTraceMeters");
			return s != null ? Double.parseDouble(s) : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	protected void setHardTraceMeters(double hardTraceMeters) {
		setData("hardTraceMeters", "" + hardTraceMeters);
	}

	private synchronized void initActivity() {
		if (activityClient == null) {
			NSRLog.d(TAG, "initActivity");
			activityClient = ActivityRecognition.getClient(ctx);
		}
	}

	protected synchronized void traceActivity() {
		NSRLog.d(TAG, "traceActivity");
		try {
			JSONObject conf = getConf();
			if (conf != null && getBoolean(conf.getJSONObject("activity"), "enabled")) {
				initActivity();
				long time = conf.getLong("time") * 1000;
				NSRLog.d(TAG, "requestActivityUpdates");
				activityIntent = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, NSRActivityCallback.class), PendingIntent.FLAG_UPDATE_CURRENT);
				activityClient.requestActivityUpdates(time, activityIntent);
			}
		} catch (JSONException e) {
			NSRLog.e(TAG, "traceActivity", e);
		}
	}

	protected synchronized void stopTraceActivity() {
		if (activityClient != null && activityIntent != null) {
			NSRLog.d(TAG, "stopTraceActivity");
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
		NSRLog.d(TAG, "tracePower");
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
			NSRLog.e(NSR.TAG, "tracePower", e);
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
		NSRLog.d(TAG, "traceConnection");
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
			NSRLog.e(NSR.TAG, "traceConnection", e);
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
				sendEvent("locationAuth", payload);
			}

			String pushAuth = (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) ? "authorized" : "notAuthorized";
			String lastPushAuth = getLastPushAuth();
			if (!pushAuth.equals(lastPushAuth)) {
				setLastPushAuth(pushAuth);
				JSONObject payload = new JSONObject();
				payload.put("status", pushAuth);
				sendEvent("pushAuth", payload);
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
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		setData("securityDelegateClass", securityDelegate.getClass().getName());
		this.securityDelegate = securityDelegate;
	}

	protected NSRWorkflowDelegate getWorkflowDelegate() {
		return workflowDelegate;
	}

	public void setWorkflowDelegate(NSRWorkflowDelegate workflowDelegate) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		setData("workflowDelegateClass", workflowDelegate.getClass().getName());
		this.workflowDelegate = workflowDelegate;
	}

	protected NSRPushDelegate getPushDelegate() {
		return pushDelegate;
	}

	public void setPushDelegate(NSRPushDelegate pushDelegate) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		setData("pushDelegateClass", pushDelegate.getClass().getName());
		this.pushDelegate = pushDelegate;
	}


	public void setup(final JSONObject settings) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.enabled = !getBoolean(settings, "disable_log");
		NSRLog.d(TAG, "setup");
		try {
			if (!settings.has("ns_lang")) {
				settings.put("ns_lang", Locale.getDefault().getLanguage());
			}
			if (!settings.has("push_icon")) {
				settings.put("push_icon", R.drawable.nsr_logo);
			}
			if (settings.has("skin")) {
				storeData("skin", settings.getJSONObject("skin"));
			}
			NSRLog.d(TAG, "setup: " + settings);
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
			NSRLog.e(TAG, "setup", e);
		}
	}

	public void registerUser(NSRUser user) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.d(TAG, "registerUser");
		try {
			setAuth(null);
			setUser(user);
			authorize(new NSRAuth() {
				public void authorized(boolean authorized) throws Exception {
					NSRLog.d(TAG, "registerUser: " + (authorized ? "" : "not ") + "authorized!");
					if (authorized && getBoolean(getConf(), "send_user")) {
						NSRLog.d(TAG, "sendUser");
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
							NSRLog.d(TAG, "sendUser token: " + token);
							headers.put("ns_token", token);
							headers.put("ns_lang", getLang());

							NSRLog.d(TAG, "requestPayload: " + requestPayload.toString());

							getSecurityDelegate().secureRequest(ctx, "register", requestPayload, headers, new NSRSecurityResponse() {
								public void completionHandler(JSONObject json, String error) throws Exception {
									if (error != null) {
										NSRLog.e(TAG, "sendUser secureRequest: " + error);
									}
								}
							});
						} catch (Exception e) {
							NSRLog.e(TAG, "sendUser", e);
						}
					}
				}
			});
		} catch (Exception e) {
			NSRLog.e(TAG, "registerUser", e);
		}
	}

	public void forgetUser() {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.d(TAG, "forgetUser");
		setConf(null);
		setAuth(null);
		setAppURL(null);
		setUser(null);
		initJob();
	}

	protected void authorize(final NSRAuth delegate) throws Exception {
		NSRLog.d(TAG, "authorize");
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
								NSRLog.d(TAG, "authorize auth: " + auth);
								setAuth(auth);

								JSONObject oldConf = getConf();
								JSONObject conf = response.getJSONObject("conf");
								NSRLog.d(TAG, "authorize conf: " + conf);
								setConf(conf);

								String appUrl = response.getString("app_url");
								NSRLog.d(TAG, "authorize appUrl: " + appUrl);
								setAppURL(appUrl);

								if (needsInitJob(conf, oldConf)) {
									NSRLog.d(TAG, "authorize needsInitJob");
									initJob();
								} else {
									synchEventWebView();
								}
								delegate.authorized(true);
							} else {
								delegate.authorized(false);
							}
						}
					});
				} catch (Exception e) {
					NSRLog.e(TAG, "authorize", e);
					delegate.authorized(false);
				}
			}
		}
	}

	public void sendAction(final String name, final String policyCode, final String details) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.d(TAG, "sendAction - name: " + name + " policyCode: " + policyCode + " details: " + details);
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
					NSRLog.d(TAG, "sendAction token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					getSecurityDelegate().secureRequest(ctx, "trace", requestPayload, headers, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error != null) {
								NSRLog.e(TAG, "sendAction: " + error);
							} else {
								NSRLog.d(TAG, "sendAction: " + json.toString());
							}
						}
					});
				}
			});
		} catch (Exception e) {
			NSRLog.e(TAG, "sendAction", e);
		}
	}

	public void crunchEvent(final String event, final JSONObject payload) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		if (getBoolean(getConf(), "local_tracking")) {
			NSRLog.d(NSR.TAG, "crunchEvent: " + event + " payload: " + payload.toString());
			snapshot(event, payload);
			localCrunchEvent(event, payload);
		} else {
			sendEvent(event, payload);
		}
	}

	private void localCrunchEvent(final String event, final JSONObject payload) {
		if (eventWebView == null) {
			NSRLog.d(NSR.TAG, "localCrunchEvent Making NSREventWebView");
			eventWebView = new NSREventWebView(ctx, this);
		}
		NSRLog.d(NSR.TAG, "localCrunchEvent call eventWebView");
		eventWebView.crunchEvent(event, payload);
	}

	public void sendEvent(final String event, final JSONObject payload) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.d(TAG, "sendEvent - event: " + event + " payload: " + payload);
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
					NSRLog.d(TAG, "sendEvent token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					NSRLog.d(TAG, "requestPayload: " + requestPayload.toString());

					getSecurityDelegate().secureRequest(ctx, "event", requestPayload, headers, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error == null) {
								if (json.has("pushes")) {
									boolean skipPush = !json.has("skipPush") || getBoolean(json, "skipPush");
									JSONArray pushes = json.getJSONArray("pushes");
									if (!skipPush) {
										if (pushes.length() > 0) {
											JSONObject push = pushes.getJSONObject(0);
											showPush(push);
											if (getBoolean(getConf(), "local_tracking")) {
												localCrunchEvent("pushed", push);
											}
										}
									} else {
										if (pushes.length() > 0) {
											JSONObject notification = pushes.getJSONObject(0);
											NSRLog.d(TAG, notification.toString());
											showUrl(notification.getString("url"));
										}
									}
								}
							} else {
								NSRLog.e(TAG, "sendEvent secureRequest: " + error);
							}
						}
					});
				}
			});
		} catch (Exception e) {
			NSRLog.e(TAG, "sendEvent", e);
		}
	}

	public void policies(final JSONObject criteria, final NSRSecurityResponse responseHandler) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.d(TAG, "policies - criteria: " + criteria);
		try {
			authorize(new NSRAuth() {
				public void authorized(boolean authorized) throws Exception {
					if (!authorized) {
						return;
					}
					JSONObject requestPayload = new JSONObject();
					requestPayload.put("criteria", criteria);

					JSONObject headers = new JSONObject();
					String token = getToken();
					NSRLog.d(TAG, "sendEvent token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					NSRLog.d(TAG, "requestPayload: " + requestPayload.toString());

					getSecurityDelegate().secureRequest(ctx, "policies", requestPayload, headers, responseHandler);
				}
			});
		} catch (Exception e) {
			NSRLog.e(TAG, "policies", e);
		}
	}

	public void archiveEvent(final String event, final JSONObject payload) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		NSRLog.d(TAG, "archiveEvent - event: " + event + " payload: " + payload);
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
					NSRLog.d(TAG, "archiveEvent token: " + token);
					headers.put("ns_token", token);
					headers.put("ns_lang", getLang());

					NSRLog.d(TAG, "requestPayload: " + requestPayload.toString());

					getSecurityDelegate().secureRequest(ctx, "archiveEvent", requestPayload, headers, new NSRSecurityResponse() {
						public void completionHandler(JSONObject json, String error) throws Exception {
							if (error != null) {
								NSRLog.e(TAG, "archiveEvent secureRequest: " + error);
							}
						}
					});
				}
			});
		} catch (Exception e) {
			NSRLog.e(TAG, "archiveEvent", e);
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
		if (ctx == null || gracefulDegradate()) {
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
			NSRLog.e(TAG, "showUrl", e);
		}
	}

	public void closeView() {
		if (gracefulDegradate()) {
			return;
		}
		try {
			if (activityWebView != null)
				activityWebView.finish();
		} catch (Exception e) {
			NSRLog.e(TAG, "closeView", e);
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
			NSRLog.e(TAG, "getUser", e);
			return null;
		}
	}

	protected void showPush(String pid, final JSONObject push, int delay) {
		if (Build.VERSION.SDK_INT >= 21) {
			Intent intent = new Intent(ctx, NSRDelayedPush.class);
			intent.putExtra("push", push.toString());
			PendingIntent pushIntent = PendingIntent.getBroadcast(ctx, pid.hashCode(), intent, PendingIntent.FLAG_ONE_SHOT);
			((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay * 1000, pushIntent);
		}
	}

	protected void killPush(String pid) {
		if (Build.VERSION.SDK_INT >= 21) {
			((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(PendingIntent.getBroadcast(ctx, pid.hashCode(), new Intent(ctx, NSRDelayedPush.class), PendingIntent.FLAG_ONE_SHOT));
		}
	}

	protected void showPush(JSONObject push) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		try {
			if (Build.VERSION.SDK_INT >= 26) {
				NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
				NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
				if (channel == null) {
					channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH);
					channel.setSound(Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.push), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
					notificationManager.createNotificationChannel(channel);
				}
			}
			NotificationCompat.Builder notification = new NotificationCompat.Builder(ctx, CHANNEL_ID);
			notification.setSound(Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.push));
			try {
				notification.setSmallIcon(NSR.getInstance(ctx).getSettings().getInt("push_icon"));
			} catch (Exception e) {
				notification.setSmallIcon(R.drawable.nsr_logo);
			}
			if (push.has("title") && push.getString("title").trim() != "") {
				notification.setContentTitle(push.getString("title"));
			}
			notification.setContentText(push.getString("body"));
			notification.setStyle(new NotificationCompat.BigTextStyle().bigText(push.getString("body")));
			notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
			notification.setPriority(NotificationCompat.PRIORITY_HIGH);

			notification.setAutoCancel(true);
			String url = push.has("url") ? push.getString("url") : null;
			PendingIntent pendingIntent = null;
			if (url != null && !"".equals(url)) {
				if (getPushDelegate() != null) {
					pendingIntent = getPushDelegate().makePendingIntent(ctx, push);
				} else {
					pendingIntent = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), makeActivityWebView(url), PendingIntent.FLAG_UPDATE_CURRENT);
				}
			}
			if (pendingIntent != null) {
				notification.setContentIntent(pendingIntent);
			}
			NotificationManagerCompat.from(ctx).notify((int) System.currentTimeMillis(), notification.build());
		} catch (Exception e) {
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
			NSRLog.e(TAG, "getToken", e);
			return null;
		}
	}

	protected String getPushToken() {
		try {
			return getSettings().has("push_token") ? getSettings().getString("push_token") : null;
		} catch (Exception e) {
			NSRLog.e(TAG, "getPushToken", e);
			return null;
		}
	}

	protected String getLang() {
		try {
			return getSettings().has("ns_lang") ? getSettings().getString("ns_lang") : null;
		} catch (Exception e) {
			NSRLog.e(TAG, "getLang", e);
			return null;
		}
	}

	protected boolean isLogEnabled() {
		return !getBoolean(getSettings(), "disable_log");
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
			try {
				return tod(getSharedPreferences().getString(key, ""));
			} catch (Exception e) {
				return null;
			}
		} else {
			return null;
		}
	}

	protected void setData(String key, String value) {
		SharedPreferences.Editor editor = getSharedPreferences().edit();
		if (value != null) {
			try {
				editor.putString(key, toe(value));
			} catch (Exception e) {
			}
		} else {
			editor.remove(key);
		}
		editor.commit();
	}

	protected JSONObject getJSONData(String key) {
		try {
			return new JSONObject(getData(key));
		} catch (Exception e) {
			return null;
		}
	}

	protected void setJSONData(String key, JSONObject value) {
		setData(key, (value != null) ? value.toString() : null);
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
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		try {
			JSONObject params = new JSONObject();
			params.put("loginExecuted", "yes");
			showUrl(url, params);
		} catch (Exception e) {
			NSRLog.e(TAG, "loginExecuted", e);
		}
	}

	public void paymentExecuted(JSONObject paymentInfo, String url) {
		if (ctx == null || gracefulDegradate()) {
			return;
		}
		try {
			JSONObject params = new JSONObject();
			params.put("paymentExecuted", paymentInfo.toString());
			showUrl(url, params);
		} catch (Exception e) {
			NSRLog.e(TAG, "paymentExecuted", e);
		}
	}

	public void storeData(String key, JSONObject data) {
		setJSONData("WV_" + key, data);
	}

	public JSONObject retrieveData(String key) {
		return getJSONData("WV_" + key);
	}

	private String tod(String input) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOf(K, 16), "AES"), new IvParameterSpec(Arrays.copyOf(I, 16)));
		return new String(cipher.doFinal(Base64.decode(input, Base64.NO_WRAP)));
	}

	private String toe(String input) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOf(K, 16), "AES"), new IvParameterSpec(Arrays.copyOf(I, 16)));
		return Base64.encodeToString(cipher.doFinal(input.getBytes()), Base64.NO_WRAP);
	}
}
