package biz.bokhorst.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		Log.w("AM", "Boot receiver, action=" + intent.getAction());
		Intent initService = new Intent(context, BackgroundService.class);
		initService.setAction(BackgroundService.ACTION_START);
		context.startService(initService);
	}
}
