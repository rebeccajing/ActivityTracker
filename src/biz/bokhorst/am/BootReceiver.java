package biz.bokhorst.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, Intent bootIntent) {
		Intent initService = new Intent(context, BackgroundService.class);
		initService.setAction(BackgroundService.ACTION_START);
		context.startService(initService);
	}
}
