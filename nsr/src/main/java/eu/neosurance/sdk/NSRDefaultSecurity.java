package eu.neosurance.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONObject;

import java.util.Iterator;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

public class NSRDefaultSecurity implements NSRSecurityDelegate {

	public void secureRequest(final Context ctx, final String endpoint, final JSONObject payload, final JSONObject headers, final NSRSecurityResponse completionHandler) throws Exception {

		new Handler(Looper.getMainLooper()).post(new Runnable() {
			public void run() {
				try {
					final AsyncHttpClient client = new AsyncHttpClient();
					final String url = NSR.getInstance(ctx).getSettings().getString("base_url") + endpoint;
					NSRLog.d(NSR.TAG, "NSRDefaultSecurity: " + url);
					if (headers != null) {
						Iterator<String> keys = headers.keys();
						while (keys.hasNext()) {
							String key = keys.next();
							client.addHeader(key, headers.getString(key));
						}
					}
					StringEntity entity = new StringEntity(payload != null ? payload.toString() : "", "UTF-8");
					entity.setContentType("application/json;charset=UTF-8");

					client.post(ctx, url, entity, "application/json;charset=UTF-8", new AsyncHttpResponseHandler() {
						public void onSuccess(int statusCode, Header[] headers, byte[] response) {
							try {
								String s =new String(response, "UTF-8");
								NSRLog.d(NSR.TAG, "response: " + s);
								JSONObject json = new JSONObject(s);
								completionHandler.completionHandler(json, null);
							} catch (Exception e) {
								NSRLog.e(NSR.TAG, e.getMessage());
							}
						}

						public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable error) {
							try {
								completionHandler.completionHandler(null, error.toString());
							} catch (Exception e) {
								NSRLog.e(NSR.TAG, e.getMessage());
							}
						}
					});
				} catch (Exception e) {
					NSRLog.e(NSR.TAG, e.getMessage());
				}
			}
		});
	}
}