package eu.neosurance.sdk;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;

import org.json.JSONObject;

import java.util.Iterator;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class NSRDefaultSecurity implements NSRSecurityDelegate {

	public void secureRequest(final Context ctx, final String endpoint, final JSONObject payload, final JSONObject headers, final NSRSecurityResponse completionHandler) throws Exception {
		try {
			String url = NSR.getInstance(ctx).getSettings().getString("base_url") + endpoint;
			NSRLog.d(NSR.TAG, "NSRDefaultSecurity: " + url);
			AsynchRequest asynchRequest = new AsynchRequest(url, payload, headers, completionHandler);
			asynchRequest.execute();
		} catch (Exception e) {
			NSRLog.e(NSR.TAG, e.getMessage());
			throw e;
		}
	}

	private class AsynchRequest extends AsyncTask<Void, Void, Object> {
		private String url;
		private JSONObject payload;
		private JSONObject headers;
		private NSRSecurityResponse completionHandler;

		public AsynchRequest(String url, JSONObject payload, JSONObject headers, NSRSecurityResponse completionHandler) {
			this.url = url;
			this.payload = payload;
			this.headers = headers;
			this.completionHandler = completionHandler;
		}

		protected Object doInBackground(Void... params) {
			NSRHttpRunner httpRunner = null;
			try {
				httpRunner = new NSRHttpRunner(url);
				if (payload != null)
					httpRunner.payload(payload.toString(), "application/json;charset=UTF-8");

				if (headers != null) {
					Iterator<String> keys = headers.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						httpRunner.header(key, headers.getString(key));
					}
				}
				completionHandler.completionHandler(new JSONObject(httpRunner.read()), null);
			} catch (Exception e) {
				try {
					if (httpRunner != null) {
						NSRLog.e(NSR.TAG, httpRunner.getMessage());
					}
					completionHandler.completionHandler(null, e.toString());
				} catch (Exception ee) {
					NSRLog.e(NSR.TAG, ee.getMessage());
				}
			}
			return null;
		}
	}
}