package eu.neosurance.nsrsample;

import android.content.Context;
import org.json.JSONObject;
import eu.neosurance.sdk.NSRWorkflowDelegate;

public class WFDelegate implements NSRWorkflowDelegate{

	public String url = null;

	public boolean executeLogin(final Context ctx, final String url) {
		this.url = url;
		return true;
	}

	public JSONObject executePayment(final Context ctx, final JSONObject payment, final String url) {
		this.url = url;
		return  null;
	}
}
