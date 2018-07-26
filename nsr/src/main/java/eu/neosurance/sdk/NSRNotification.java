package eu.neosurance.sdk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.FileAsyncHttpResponseHandler;

import java.io.File;

import cz.msebera.android.httpclient.Header;

public class NSRNotification {
	private static final String CHANNEL_ID = "NSR_CH_ID";
	private static boolean channelcreated = false;

	public static void sendNotification(final Context ctx, final String title, final String body, final String imageUrl, final PendingIntent pendingIntent) {
		if (NSR.gracefulDegradate()) {
			return;
		}
		if (!channelcreated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			channelcreated = true;
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, NSR.TAG, NotificationManager.IMPORTANCE_HIGH);
			channel.setDescription(NSR.TAG);

			channel.setSound(Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.push), new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_NOTIFICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.build());

			NotificationManager notificationManager = (NotificationManager) (ctx).getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.createNotificationChannel(channel);
		}
		if (imageUrl != null) {
			AsyncHttpClient client = new AsyncHttpClient();
			client.get(imageUrl, new FileAsyncHttpResponseHandler(ctx) {
				public void onFailure(int statusCode, Header[] headers, Throwable throwable, File file) {
					Log.d(NSR.TAG, throwable.toString());
				}

				public void onSuccess(int statusCode, Header[] headers, File response) {
					if (response != null || response.exists()) {
						NSRNotification.showNotification(ctx, title, body, BitmapFactory.decodeFile(file.getAbsolutePath()), pendingIntent);
					}
				}
			});
		} else {
			showNotification(ctx, title, body, null, pendingIntent);
		}
	}

	private static void showNotification(Context ctx, String title, String body, Bitmap image, PendingIntent pendingIntent) {
		NotificationCompat.Builder notification = null;
		notification = new NotificationCompat.Builder(ctx, CHANNEL_ID);
		notification.setSound(Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.push));
		try {
			notification.setSmallIcon(NSR.getInstance(ctx).getSettings().getInt("push_icon"));
		} catch (Exception e) {
			notification.setSmallIcon(R.drawable.nsr_logo);
		}
		notification.setContentTitle(title);
		notification.setContentText(body);
		notification.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
		notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		notification.setPriority(NotificationCompat.PRIORITY_HIGH);
		notification.setAutoCancel(true);
		if (image != null) {
			notification.setLargeIcon(image);
		}
		if (pendingIntent != null) {
			notification.setContentIntent(pendingIntent);
		}
		NotificationManagerCompat.from(ctx).notify((int) System.currentTimeMillis(), notification.build());
	}

}
