package com.dynamicode.fw.update;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

public class UpdateService extends Service {

	private String apkurl = "http://192.168.100.116:8080//ServletTest/apk/ota.bin";
//	private String apkurl = "http://192.168.155.1:8080//ServletTest/apk/ota.bin";
	private String apkPath;
	private String apkName;
	private boolean canceled = false;
	private NotificationManager manager;
	private Notification notification;
	private final String tag = "P92DCSTART";
	private final long RE_DOWN_DELAY = 60000;   //当网络无法连接时重新下载间隔时间
	private final int MAX_RE_DOWN_NUMBER = 10;	 //当网络无法连接时最大重新下载次数
	private int current_redown_number = 1;		 //当前重新下载次数
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/dcota";
			apkName = "ota.bin";
			registerBroader();
			startDownload();
			Log.e(tag,"start");
		} else {
			Toast.makeText(UpdateService.this, "SD卡不存在", Toast.LENGTH_SHORT)
					.show();
		}
		
	}

	/**
	 * 创建通知
	 */
	private void setUpNotifiction() {
		manager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "开始下载";
		long when = System.currentTimeMillis();
		notification = new Notification();
		notification.icon = icon;
		notification.tickerText = tickerText;
		notification.when = when;

		RemoteViews contentView = new RemoteViews(getPackageName(),
				R.layout.notify_update);
		contentView.setTextViewText(R.id.name, "固件正在下载中");
		
		Intent canceledIntent = new Intent("canceled");
		canceledIntent.putExtra("canceled", "canceled");
		PendingIntent canceledPendingIntent = PendingIntent.getBroadcast(
				UpdateService.this, 1, canceledIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		contentView.setOnClickPendingIntent(R.id.cancle, canceledPendingIntent);
		
		Intent updateFWIntent = new Intent("updateFW");
		updateFWIntent.putExtra("updateFW", "updateFW");
		PendingIntent updateFWPendingIntent = PendingIntent.getBroadcast(UpdateService.this, 2, 
				updateFWIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		contentView.setOnClickPendingIntent(R.id.update, updateFWPendingIntent);
		contentView.setViewVisibility(R.id.update, View.GONE);
		notification.contentView = contentView;

		/**取消通知栏点击事件，点击通知栏时不做页面的跳转
		Intent intent = new Intent(UpdateService.this, MainActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(
				UpdateService.this, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		notification.contentIntent = contentIntent;
		 */
		manager.notify(0, notification);// 发送通知
	}

	/**
	 * 取消接收者
	 * 
	 * @author renzhiwen 创建时间 2014-8-16 下午4:05:24
	 */
	class CanceledReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if ("canceled".equals(intent.getStringExtra("canceled"))) {
				canceled = true;
				manager.cancel(0);
				stopSelf();
			}else if("updateFW".equals(intent.getStringExtra("updateFW"))) {
				update();
				manager.cancel(0);
				stopSelf();
			}
		}

	}

	/**
	 * 注册广播
	 */
	public void registerBroader() {
		IntentFilter filter = new IntentFilter();
		filter.addAction("canceled");
		filter.addAction("updateFW");
		registerReceiver(new CanceledReceiver(), filter);
	}

	/**
	 * 下载apk
	 * 
	 * @author renzhiwen 创建时间 2014-8-16 下午3:32:34
	 */
	class DownApkRunnable implements Runnable {

		@Override
		public void run() {
			downloadApk();
		}

	}
	
	public void startDownload() {
		new Thread(new DownApkRunnable()).start();
	}

	private int laterate = 0;

	private void downloadApk() {
		try {
			URL url = new URL(apkurl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			int length = conn.getContentLength();
			if(length <= 0) {
				handler.sendEmptyMessageDelayed(4, RE_DOWN_DELAY*current_redown_number*current_redown_number);
				return;
			}
			int count = 0;
			File apkPathFile = new File(apkPath);
			if (!apkPathFile.exists()) {
				apkPathFile.mkdir();
			}
			File apkFile = new File(apkPath, apkName);
			InputStream in = conn.getInputStream();
			handler.sendEmptyMessage(1);
			FileOutputStream os = new FileOutputStream(apkFile);
			byte[] buffer = new byte[1024];
			do {
				int numread = in.read(buffer);
				count += numread;
				int progress = (int) (((float) count / length) * 100);// 得到当前进度
				if (progress >= laterate + 1) {// 只有当前进度比上一次进度大于等于1，才可以更新进度
					laterate = progress;
					Message msg = new Message();
					msg.what = 2;
					msg.arg1 = progress;
					handler.sendMessage(msg);
				}
				if (numread <= 0) {// 下载完毕
					handler.sendEmptyMessage(3);
					canceled = true;
					break;
				}
				os.write(buffer, 0, numread);
			} while (!canceled);// 如果没有被取消
			in.close();
			os.close();
			conn.disconnect();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.toString();
			e.printStackTrace();
		}

	}

	@SuppressLint("HandlerLeak")
	Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case 1:
				setUpNotifiction();
			case 2:// 更新进度
				int progress = msg.arg1;
				if (progress <= 100) {
					RemoteViews contentView = notification.contentView;
					contentView.setTextViewText(R.id.tv_progress, progress
							+ "%");
					contentView.setProgressBar(R.id.progressbar, 100, progress,
							false);
				} else {// 下载完成，停止服务
					stopSelf();
				}
				manager.notify(0, notification);
				break;
			case 3:// 取消按扭隐藏，更新按钮显示
				notification.contentView.setViewVisibility(R.id.cancle, View.GONE);
				notification.contentView.setViewVisibility(R.id.update, View.VISIBLE);
				manager.notify(0, notification);
				break;
			case 4:// 重新下载
				if(current_redown_number <= MAX_RE_DOWN_NUMBER) {
					current_redown_number = current_redown_number + 1;
					Log.e(tag, "current_redown_number:"+current_redown_number);
					startDownload();
				}
			default:
				break;
			}
		}
	};

	
	/**
	 * 更新固件
	 */
	private void update() {
		Intent intent = new Intent(getBaseContext(), MainActivity.class);   
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("updateFW", "updateFW");
		getApplication().startActivity(intent);   
	}
	
}
