package eu.neosurance.sdk;

public class NSRLog {
	static public boolean enabled = true;

	public static void i(String tag, String string) {
		if (enabled) android.util.Log.i(tag, string);
	}

	public static void e(String tag, String string) {
		android.util.Log.e(tag, string);
	}

	public static void e(String tag, String string, Throwable t) {
		android.util.Log.e(tag, string, t);
	}

	public static void d(String tag, String string) {
		if (enabled) android.util.Log.d(tag, string);
	}

	public static void v(String tag, String string) {
		if (enabled) android.util.Log.v(tag, string);
	}

	public static void w(String tag, String string) {
		if (enabled) android.util.Log.w(tag, string);
	}
}
