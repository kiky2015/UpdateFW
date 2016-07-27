package com.dynamicode.fw.update;

import android.provider.Settings;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

public class mBootBroadcastReceiver extends BroadcastReceiver {

	static final String ACTION = "android.intent.action.BOOT_COMPLETED";
	private Context mContext;
	private final String tag = "P92DCSTART";
	private Handler handler = new Handler() {
		@SuppressWarnings("deprecation")
		public void handleMessage(android.os.Message msg) {
			if(Settings.Secure.getInt(mContext.getContentResolver(),Settings.Secure.DEVICE_PROVISIONED, 0) == 1) {
				Intent serviceIntent = new Intent(mContext, UpdateService.class);
				serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				mContext.startService(serviceIntent);
				Log.e(tag,"mBootBroadcastReceiver start");
			}else {
				handler.sendEmptyMessageDelayed(0, 100000);
				Log.e(tag,"restart");
			}
		};
	};

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(ACTION)) {
			this.mContext = context;
			Message msg = handler.obtainMessage();
			msg.sendToTarget();
		}
	}

}
