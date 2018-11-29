package eu.neosurance.sdk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class NSRForeground extends Service {
	protected static final String SILENT_ID = "NSR_Silent";

	public NSRForeground() {
		super();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		NSRLog.d(NSR.TAG, "NSRForeground onCreate");
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationChannel channel = new NotificationChannel(SILENT_ID, SILENT_ID, NotificationManager.IMPORTANCE_NONE);
			channel.setSound(null, null);
			notificationManager.createNotificationChannel(channel);
			NotificationCompat.Builder notification = new NotificationCompat.Builder(this, SILENT_ID);
			notification.setSound(null);
			notification.setPriority(NotificationCompat.PRIORITY_MIN);
			startForeground(1, notification.build());
			notificationManager.deleteNotificationChannel(SILENT_ID);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
