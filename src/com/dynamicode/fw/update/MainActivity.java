package com.dynamicode.fw.update;

import com.lkl.cloudpos.aidl.AidlDeviceService;
import com.lkl.cloudpos.aidl.system.AidlSystem;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends Activity {

	private AidlSystem systemInf = null;
	private ProgressDialog proDlg = null;
	private TabChangeReceiver receiver = null;

	public final String LKL_SERVICE_ACTION = "lkl_cloudpos_device_service";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		bindService();
		// 实例化
		proDlg = new ProgressDialog(this);
		proDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		proDlg.setMessage("更新进度:");
		proDlg.setProgress(0);
		proDlg.setCancelable(false);

		receiver = new TabChangeReceiver();
		registerReceiver(receiver, new IntentFilter("main_pbar_view"), null, handler);

		Intent intent = getIntent();
		String stringExtra = intent.getStringExtra("updateFW");
		Log.e("UpdateFW", "stringExtra = "+stringExtra);
		if ("updateFW".equals(stringExtra)) {
			handler.sendEmptyMessageDelayed(0, 1000);
		}
	}

	// 设别服务连接桥
	private ServiceConnection conn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
			if (serviceBinder != null) { // 绑定成功
				AidlDeviceService serviceManager = AidlDeviceService.Stub.asInterface(serviceBinder);
				try {
					Log.e("UpdateFW", "bind");
					systemInf = AidlSystem.Stub.asInterface(serviceManager.getSystemService());

				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}
	};

	// 绑定服务
	public boolean bindService() {
		Intent intent = new Intent();
		intent.setAction(LKL_SERVICE_ACTION);
		boolean flag = bindService(intent, conn, Context.BIND_AUTO_CREATE);
		return flag;
	}

	public class TabChangeReceiver extends android.content.BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			if (intent.getAction().equals("main_pbar_view")) {
				int intExtra = intent.getIntExtra("rate", 0);
				handler.sendMessage(handler.obtainMessage(1, intExtra, 0));
			}
		}
	}

	@SuppressLint("HandlerLeak")
	Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case 0:
				if (systemInf != null) {
					try {
						Log.e("UpdateFW","update");
						systemInf.update(0x01);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}else {
					handler.sendEmptyMessageDelayed(0, 1000);
				}
				break;
			case 1:
				proDlg.setProgress(msg.arg1);
				if (msg.arg1 < 0) {
					if (proDlg.isShowing()) {
						proDlg.cancel();
					}
					Toast.makeText(MainActivity.this, "更新失败", Toast.LENGTH_LONG).show();
				} else if (msg.arg1 == 100) {
					if (proDlg.isShowing()) {
						proDlg.cancel();
						Toast.makeText(MainActivity.this, "更新成功", Toast.LENGTH_LONG).show();
						handler.sendEmptyMessageDelayed(2, 1000);
					}
				} else {
					if (!proDlg.isShowing()) {
						proDlg.show();
					}
				}
				break;
			case 2:
				break;
			default:
				break;
			}
		}
	};

}
