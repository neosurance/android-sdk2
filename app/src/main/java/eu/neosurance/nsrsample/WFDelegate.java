package eu.neosurance.nsrsample;

import android.content.Context;

import org.json.JSONObject;

import eu.neosurance.sdk.NSRWorkflowDelegate;

public class WFDelegate implements NSRWorkflowDelegate{
	public boolean executeLogin(Context ctx, String url) {

		return false;
	}

	public JSONObject executePayment(Context ctx, JSONObject payment, String url) {
		return null;
	}
}
