package biz.bokhorst.am;

/*
 Copyright 2014 Marcel Bokhorst
 All Rights Reserved

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		Log.w("AM", "Receiver, action=" + intent.getAction());
		Intent initService = new Intent(context, BackgroundService.class);
		initService.setAction(BackgroundService.ACTION_START);
		context.startService(initService);

		// Reset step counter
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			Log.w("AM", "Resetting step count");
			SharedPreferences prefs = context.getSharedPreferences("activity",
					Context.MODE_MULTI_PROCESS);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt("Steps", 0);
			editor.commit();
		}
	}
}
