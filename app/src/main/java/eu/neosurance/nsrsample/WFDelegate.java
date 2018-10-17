package eu.neosurance.nsrsample;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import eu.neosurance.sdk.NSRWorkflowDelegate;

public class WFDelegate implements NSRWorkflowDelegate {


	public boolean executeLogin(final Context ctx, final String url) {
		setData(ctx, "login_url", url);
		return true;
	}

	public JSONObject executePayment(final Context ctx, final JSONObject payment, final String url) {
		setData(ctx, "payment_url", url);
		return null;
	}

	public static String getData(Context ctx, String key) {
		SharedPreferences sp = ctx.getSharedPreferences("NSR_SAMPLE", Application.MODE_PRIVATE);
		if (sp.contains(key)) {
			return sp.getString(key, "");
		} else {
			return null;
		}
	}

	public static void setData(Context ctx, String key, String value) {
		SharedPreferences sp = ctx.getSharedPreferences("NSR_SAMPLE", Application.MODE_PRIVATE);
		SharedPreferences.Editor editor = sp.edit();
		if (value != null) {
			editor.putString(key, value);
		} else {
			editor.remove(key);
		}
		editor.commit();
	}
}
