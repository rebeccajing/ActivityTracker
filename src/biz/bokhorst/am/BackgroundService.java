package biz.bokhorst.am;

import android.app.IntentService;
import android.content.Intent;

public class BackgroundService extends IntentService {

	public BackgroundService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
	}
}
