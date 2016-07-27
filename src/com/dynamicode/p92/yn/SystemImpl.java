package com.dynamicode.p92.yn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import com.dynamicode.dc.connect.IConnect;
import com.dynamicode.impl.DCDevice;
import com.dynamicode.sdk.IDevice;
import com.dynamicode.sdk.IDeviceManager;
import com.dynamicode.sdk.IDevice.OnInitListener;
import com.dynamicode.sdk.IDeviceManager.UpdataListener;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RecoverySystem;
import android.util.Log;
import android.widget.Toast;
import android.telephony.TelephonyManager;

public class SystemImpl {
	private final static String TAG = "SystemImpl";
	private Context context;
	private WakeLock mWakeLock;

	private IDevice dcpos;
	private IDeviceManager mDeviceManager;
	private InstallAppObserver installObserver;
	private String installPath = "";
	private byte[] firmware;
	private int fileLength;
	private Bundle sysInfo = null;
	private Notification mNotification;
	private static boolean isUpdating = false;
	private static boolean isReReadDeviceParams = false;
	private File[] apkFiles;
	private ArrayList<String> fileList = new ArrayList<String>();
	private static int index = 0;
	private static final int CMD_UPDATE = 0;// 升级
	private static final int CMD_UPDATE_CANCEL = 1;// 取消
	private static final int CMD_UPDATE_ERR = 2;// 取消
	private static final int CMD_INSTALL_APK = 3;// 安装apk
	private static final int CMD_INSTALL_FAIL = 4;// 安装apk返回err
	private static final int CMD_INSTALL_OK = 5;// 安装apk返回ok
	private static final int CMD_SILENT_INSTALL_OK = 6;// 安装apk返回ok
	private static final int CMD_SILENT_INSTALL_OK2 = 7;// 安装apk返回ok

	private static final String otaOsFile = "/storage/sdcard0/dcota/otasys.zip";
	private static final String otaPath = "/storage/sdcard0/dcota";
	private static final String otaFile = "/storage/sdcard0/dcota/ota.zip";
	private static final String otaBinFile = "/storage/sdcard0/dcota/ota.bin";
	private static String zipFileName = "";

	public static final String UPDATE_MSG = "com.lakala.gtms.update";

	@SuppressLint("HandlerLeak")
	private Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case CMD_UPDATE:
				int rate = msg.arg1;
				if (rate < 100) {
					// 更新进度
//					 RemoteViews contentView = mNotification.contentView;
//					 contentView.setTextViewText(R.id.rate, rate + "%");
//					 contentView.setProgressBar(R.id.progress, 100, rate,
//					 false);

					// 传递数据
					final Intent intent = new Intent();
					intent.setAction("main_pbar_view");
					intent.putExtra("rate", rate);
					context.sendBroadcast(intent);
				} else {
					// 下载完毕后变换通知形式
					releaseWakeLock();
					sendUpdateBroadcast(0, "");// 发送更新完成的广播
					isUpdating = false;
					// mNotification.flags = Notification.FLAG_AUTO_CANCEL;
					// mNotification.contentView = null;
					Intent intent = new Intent();
					// PendingIntent contentIntent = PendingIntent.getService(
					// context, 0, intent, 0);
					// mNotification.setLatestEventInfo(context, context
					// .getString(updateType == 1 ? R.string.update
					// : R.string.updatefont), context
					// .getString(R.string.update_finish), contentIntent);

					// 传递数据
					intent.setAction("main_pbar_view");
					intent.putExtra("rate", 100);
					context.sendBroadcast(intent);
				}
				// 最后别忘了通知一下,否则不会更新
				// mNotificationManager.notify(NOTIFY_ID, mNotification);
				break;
			case CMD_UPDATE_CANCEL:
				// 取消通知
				isUpdating = false;
				Toast.makeText(context, (String) msg.obj, Toast.LENGTH_SHORT)
						.show();
				// mNotificationManager.cancel(NOTIFY_ID);
				break;
			case CMD_UPDATE_ERR:
				releaseWakeLock();
				sendUpdateBroadcast(1, "update fail");// 发送更新完成的广播
				isUpdating = false;
				if (mNotification == null) {
					return;
				}
				// mNotification.flags = Notification.FLAG_AUTO_CANCEL;
				// mNotification.contentView = null;
				Intent intent = new Intent();
				// PendingIntent contentIntent =
				// PendingIntent.getService(context,
				// 0, intent, 0);
				// mNotification.setLatestEventInfo(context, context
				// .getString(updateType == 1 ? R.string.update
				// : R.string.updatefont), context
				// .getString(R.string.update_err), contentIntent);
				// // 最后别忘了通知一下,否则不会更新
				// mNotificationManager.notify(NOTIFY_ID, mNotification);

				// 传递数据
				intent.setAction("main_pbar_view");
				intent.putExtra("rate", -1);
				context.sendBroadcast(intent);
				break;
			case CMD_SILENT_INSTALL_OK2:
				if (msg.arg1 == 1) {
					Log.e(TAG, "CMD_SILENT_INSTALL_OK2 OK installPath = "
							+ installPath);
					handler.sendEmptyMessage(CMD_INSTALL_OK);
				} else {
					Log.e(TAG, "CMD_SILENT_INSTALL_OK2 FAIL installPath = "
							+ installPath);
					handler.sendEmptyMessage(CMD_INSTALL_FAIL);
				}
				break;
			case CMD_INSTALL_FAIL:
				sendUpdateBroadcast(1, "安装失败!");// 发送更新完成的广播
				Log.e(TAG, "sendUpdateBroadcast 1");
				index++;
				if (index < fileList.size()) {
					handler.sendEmptyMessage(CMD_INSTALL_APK);
				} else {
					// for(int i = 0;i<fileList.size();i++){
					// File f = new File(fileList.get(i));
					// if(f.exists()){
					// f.delete();
					// }
					// }
				}

				break;
			case CMD_INSTALL_OK:
				Log.e(TAG, "CMD_INSTALL_OK index = " + index);
				sendUpdateBroadcast(0, "安装成功!");// 发送更新完成的广播
				Log.e(TAG, "sendUpdateBroadcast 0");
				index++;
				File file = new File(installPath);
				if (file.exists()) {
					Log.e(TAG, "delete");
					file.delete();
				} else {
					Log.e(TAG, "!exists");
				}
				if (index < fileList.size()) {
					handler.sendEmptyMessage(CMD_INSTALL_APK);
				} else {
					fileList.clear();
				}

				break;
			case CMD_SILENT_INSTALL_OK:
				Log.e(TAG, "CMD_SILENT_INSTALL_OK msg.arg1 = " + msg.arg1);
				Log.e(TAG, "CMD_SILENT_INSTALL_OK OK installPath = "
						+ installPath);
				if (msg.arg1 == 1) {
					File file2 = new File(installPath);
					if (file2.exists()) {
						Log.e(TAG, "delete");
						file2.delete();
					} else {
						Log.e(TAG, "!exists");
					}
					installObserver.onInstallFinished();
				} else {
					installObserver.onInstallError(msg.arg1);
				}
			default:
				break;
			}
		};
	};

	public SystemImpl(Context ctx) {
		context = ctx;
		initSdk();
	}

	private void initSdk() {
		dcpos = DCDevice.getInstance();
		dcpos.init(context, new OnInitListener() {

			@Override
			public void onInitOk() {
				Log.d(TAG, "onInitOk");
			}

			@Override
			public void onError(String msg) {
				Log.d(TAG, "onInitError");
			}

		});
	}
	
	protected void sendUpdateBroadcast(int errCode, String info) {
		// TODO Auto-generated method stub
		Intent broadcast = new Intent(SystemImpl.UPDATE_MSG);
		JSONObject object = new JSONObject();
		try {
			object.put("fileName", zipFileName);
			if (errCode == 0) {
				object.put("respCode", "00");
			} else {
				object.put("respCode", "01");
			}
			object.put("respInfo", info);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		broadcast.putExtra("data", object.toString());
		context.sendBroadcast(broadcast, null);
	}

	@SuppressWarnings("deprecation")
	private void acquireWakeLock() {
		if (mWakeLock == null) {
			Log.d(TAG, "Acquiring wake lock");
			PowerManager pm = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, this
					.getClass().getCanonicalName());
			mWakeLock.acquire();
		}
	}

	private void releaseWakeLock() {
		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	public String getDriverVersion() {
		// TODO Auto-generated method stub
		if (sysInfo == null || sysInfo.getString("gjv") == null
				|| isReReadDeviceParams == true) {
			if (dcpos != null) {
				mDeviceManager = dcpos.openDeviceManager();
			}
			isReReadDeviceParams = false;
			sysInfo = mDeviceManager.GetDeviceParams();
		}

		String gjv = sysInfo.getString("gjv");
		return gjv;
	}


	public String getCurSdkVersion() {
		// TODO Auto-generated method stub
		String versionname = "";// 版本号
		try {
			PackageInfo pi = context.getPackageManager().getPackageInfo(
					context.getPackageName(), 0);
			versionname = pi.versionName;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return versionname;
	}

	// 将字符串转为时间戳
	@SuppressLint("SimpleDateFormat")
	public static long getTime(String user_time) {
		long l = 0;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		Date d;
		try {
			d = sdf.parse(user_time);
			l = d.getTime();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return l;
	}

	public String getStoragePath() {
		// TODO Auto-generated method stub
		// 创建文件夹
		File destDir = new File(otaPath);
		if (!destDir.exists()) {
			destDir.mkdirs();
		}
		return otaPath;
	}

	public void update(int updateType) {
		/**
		 * updateType： 0x00 Android OS 更新 0x01 驱动资源包更新
		 */
		// TODO Auto-generated method stub
		if (isUpdating) {
			// Toast.makeText(context, context.getString(R.string.isupdating),
			// Toast.LENGTH_SHORT).show();
			return;
		}
		acquireWakeLock();

		switch (updateType) {
		case 0x00: // OS
		{
			File sysFile = new File(otaOsFile);
			if (sysFile.exists()) {
				try {
					RecoverySystem.installPackage(context, sysFile);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				Log.e(TAG, otaOsFile + " is not found");
			}
		}
			break;

		case 0x01: // bin
		{
			Log.e(TAG, "update enter");
			File zipFile = new File(otaFile);
			File binFile = new File(otaBinFile);
			if (zipFile.exists()) {
				zipFileName = "ota.zip";
				if (binFile.exists()) {
					binFile.delete();
				}
				try {
					Zip.UnZipFolder(otaFile, otaPath);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					Log.e(TAG, "error :" + e1.getMessage());
					Message msg = handler.obtainMessage();
					msg.what = CMD_UPDATE_ERR;
					msg.obj = e1.getMessage();
					handler.sendMessage(msg);
					return;
				}
				if (zipFile.exists()) {
					zipFile.delete();
				}

				if (!binFile.exists()) {
					Log.e(TAG, "error : bin file not found");
					Message msg = handler.obtainMessage();
					msg.what = CMD_UPDATE_ERR;
					msg.obj = "bin file not found";
					handler.sendMessage(msg);
					return;
				}

				try {
					firmware = readFileSdcardFile(otaBinFile);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.e(TAG, "error :" + e.getMessage());
					Message msg = handler.obtainMessage();
					msg.what = CMD_UPDATE_ERR;
					msg.obj = e.getMessage();
					handler.sendMessage(msg);
					return;
				}
				isUpdating = true;
				isReReadDeviceParams = true;
				// downloadNotification();

				if (dcpos != null) {
					mDeviceManager = dcpos.openDeviceManager();
					mDeviceManager.UpdateFirmware(firmware,
							new UpdataListener() {

								@Override
								public void onEvent(int what, String info) {
									// TODO Auto-generated method stub
									if (what == IConnect.UPDATE_RUNNING) {
										Message msg = handler.obtainMessage();
										msg.what = CMD_UPDATE;
										msg.arg1 = Integer.valueOf(info);
										handler.sendMessage(msg);
									} else if (what == IConnect.UPDATE_FINISH) {
										Message msg = handler.obtainMessage();
										msg.what = CMD_UPDATE;
										msg.arg1 = Integer.valueOf(info);
										handler.sendMessage(msg);

										File binFile = new File(otaBinFile);
										if (binFile.exists()) {
											binFile.delete();
										}
									} else if (what == IConnect.UPDATE_ERR) {
										Message msg = handler.obtainMessage();
										msg.what = CMD_UPDATE_ERR;
										msg.obj = info;
										handler.sendMessage(msg);
									}
								}
							});
				}
			} else {
				if (binFile.exists()) {
					try {
						firmware = readFileSdcardFile(otaBinFile);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						Log.e(TAG, "error :" + e.getMessage());
						Message msg = handler.obtainMessage();
						msg.what = CMD_UPDATE_ERR;
						msg.obj = e.getMessage();
						handler.sendMessage(msg);
						return;
					}
					isUpdating = true;
					isReReadDeviceParams = true;
					// downloadNotification();

					if (dcpos != null) {
						mDeviceManager = dcpos.openDeviceManager();
						mDeviceManager.UpdateFirmware(firmware,
								new UpdataListener() {

									@Override
									public void onEvent(int what, String info) {
										// TODO Auto-generated method stub
										if (what == IConnect.UPDATE_RUNNING) {
											Message msg = handler
													.obtainMessage();
											msg.what = CMD_UPDATE;
											msg.arg1 = Integer.valueOf(info);
											handler.sendMessage(msg);
										} else if (what == IConnect.UPDATE_FINISH) {
											Message msg = handler
													.obtainMessage();
											msg.what = CMD_UPDATE;
											msg.arg1 = Integer.valueOf(info);
											handler.sendMessage(msg);

											File binFile = new File(otaBinFile);
											if (binFile.exists()) {
												binFile.delete();
											}
										} else if (what == IConnect.UPDATE_ERR) {
											Message msg = handler
													.obtainMessage();
											msg.what = CMD_UPDATE_ERR;
											msg.obj = info;
											handler.sendMessage(msg);
										}
									}
								});
					}
				} else {
					// 更新apk
					File rootDir = new File(otaPath);
					if (rootDir.isDirectory()) {
						// 返回文件夹中有的zip文件
						apkFiles = rootDir.listFiles();
						if (apkFiles != null) {
							if (fileList != null) {
								fileList.clear();
							}
							for (int i = 0; i < apkFiles.length; i++) {
								String filePath = apkFiles[i].getAbsolutePath();
								String fileName = filePath.substring(filePath
										.lastIndexOf("/") + 1);
								String fileSubName = fileName
										.substring(fileName.lastIndexOf(".") + 1);

								if (fileName.equals("ota.zip")
										|| fileName.equals("otasys.zip")) {

								} else {
									if (fileSubName.equalsIgnoreCase("zip")) {
										Log.e(TAG, "filelist add " + fileName);
										zipFileName = fileName;
										fileList.add(filePath);
									}
								}
							}
							if (!fileList.isEmpty()) {
								try {
									for (String strFile : fileList) {
										File f = new File(strFile);
										if (f.exists()) {
											Zip.UnZipFolder(strFile, otaPath);
										}
									}
								} catch (Exception e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
									for (File f : apkFiles) {
										if (f.exists()) {
											f.delete();
										}
									}
									Log.e(TAG, "error :" + e1.getMessage());
									Message msg = handler.obtainMessage();
									msg.what = CMD_UPDATE_ERR;
									msg.obj = e1.getMessage();
									handler.sendMessage(msg);
									return;
								}
								for (File f : apkFiles) {
									if (f.exists()) {
										f.delete();
									}
								}
							} else {
								for (File f : apkFiles) {
									if (f.exists()) {
										f.delete();
									}
								}
								Message msg = handler.obtainMessage();
								msg.what = CMD_UPDATE_ERR;
								msg.obj = "no zip file";
								handler.sendMessage(msg);
								return;
							}
						}
						// 2
						apkFiles = rootDir.listFiles();
						if (apkFiles != null) {
							if (fileList != null) {
								fileList.clear();
							}
							for (int i = 0; i < apkFiles.length; i++) {
								String filePath = apkFiles[i].getAbsolutePath();
								Log.e(TAG, "apkFiles filePath " + i + "/"
										+ filePath);
								String fileName = filePath.substring(filePath
										.lastIndexOf("/") + 1);
								String fileSubName = fileName
										.substring(fileName.lastIndexOf(".") + 1);
								if (fileSubName.equalsIgnoreCase("apk")) {
									Log.e(TAG, "fileList filePath " + i + "/"
											+ filePath);
									fileList.add(filePath);
								}
							}
							Log.e(TAG, "apkFiles len = " + apkFiles.length);
							if (!fileList.isEmpty()) {
								index = 0;
								handler.sendEmptyMessage(CMD_INSTALL_APK);
								return;
							} else {
								for (File f : apkFiles) {
									if (f.exists()) {
										f.delete();
									}
								}
								Message msg = handler.obtainMessage();
								msg.what = CMD_UPDATE_ERR;
								msg.obj = "no apk file";
								handler.sendMessage(msg);
								return;
							}
						}
					} else {
						Message msg = handler.obtainMessage();
						msg.what = CMD_UPDATE_ERR;
						msg.obj = "文件路径错误";
						handler.sendMessage(msg);
					}
				}
			}
		}
			break;

		default:
			break;
		}
	}


	// 读SD中的文件
	public byte[] readFileSdcardFile(String fileName) throws IOException {
		byte[] buffer = null;
		try {
			FileInputStream fin = new FileInputStream(fileName);
			fileLength = fin.available();

			buffer = new byte[fileLength];
			fin.read(buffer);
			fin.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return buffer;
	}

	public String getIMSI() {
		TelephonyManager telephonyManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		String imsi = telephonyManager.getSubscriberId();
		return imsi;
	}

	public String getIMEI()  {
		// TODO Auto-generated method stub
		TelephonyManager telephonyManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		String imei = telephonyManager.getDeviceId();
		return imei;
	}

	public String getManufacture()  {
		return "dynamicode";
	}

	public String getAndroidKernelVersion() {
		// TODO Auto-generated method stub

		Process process = null;
		try {
			process = Runtime.getRuntime().exec("cat /proc/version");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// get the output line
		InputStream outs = process.getInputStream();
		InputStreamReader isrout = new InputStreamReader(outs);
		BufferedReader brout = new BufferedReader(isrout, 8 * 1024);

		String result = "";
		String line;
		// get the whole standard output string
		try {
			while ((line = brout.readLine()) != null) {
				result += line;
				// result += "\n";
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (result != "") {
			String Keyword = "version ";
			int index = result.indexOf(Keyword);

			line = result.substring(index + Keyword.length());
			index = line.indexOf(" ");
			return line.substring(0, index);
		}

		return "";
	}

	public void reboot() {
		// TODO Auto-generated method stub
		PowerManager pManager = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		pManager.reboot("重启");
	}

	public boolean SetDeviceParams(Bundle bundle) {
		// TODO Auto-generated method stub
		if (dcpos != null) {
			mDeviceManager = dcpos.openDeviceManager();
		}
		sysInfo = null;
		isReReadDeviceParams = true;
		return mDeviceManager.SetDeviceParams(bundle);
	}


	public boolean restoreFactorySettings() {
		// TODO Auto-generated method stub
		if (dcpos != null) {
			mDeviceManager = dcpos.openDeviceManager();
			return mDeviceManager.restoreFactorySettings();
		}
		return false;
	}
	
	public boolean SetFont(int type, byte[] fontData) {
		// TODO Auto-generated method stub
		if (dcpos != null) {
			mDeviceManager = dcpos.openDeviceManager();
		}

		if (fontData != null && fontData.length > 0) {
			mDeviceManager.SetFont(type, fontData, new UpdataListener() {

				@Override
				public void onEvent(int what, String info) {
					// TODO Auto-generated method stub
					if (what == IConnect.UPDATE_RUNNING) {
						Message msg = handler.obtainMessage();
						msg.what = CMD_UPDATE;
						msg.arg1 = Integer.valueOf(info);
						handler.sendMessage(msg);
					} else if (what == IConnect.UPDATE_FINISH) {
						Message msg = handler.obtainMessage();
						msg.what = CMD_UPDATE;
						msg.arg1 = Integer.valueOf(info);
						handler.sendMessage(msg);
					} else if (what == IConnect.UPDATE_ERR) {
						Message msg = handler.obtainMessage();
						msg.what = CMD_UPDATE_ERR;
						msg.obj = info;
						handler.sendMessage(msg);
					}
				}
			});
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Shell 命令执行接口 此接口正常不会对外部开放，行业应用无法使用，使用了审核无法通过
	 * 
	 * @param command
	 *            - String 命令脚本
	 * @return true ： shell 命令执行成功 false ： shell 命令执行失败
	 */
	boolean executeShellCmd(String command) throws IOException {
		Runtime runtime = Runtime.getRuntime();
		Process proc = runtime.exec(command); // 这句话就是shell与高级语言间的调用
		// 如果有参数的话可以用另外一个被重载的exec方法
		// 实际上这样执行时启动了一个子进程,它没有父进程的控制台
		// 也就看不到输出,所以我们需要用输出流来得到shell执行后的输出
		InputStream inputstream = proc.getInputStream();
		InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
		BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
		// read the ls output
		String line = "";
		StringBuilder sb = new StringBuilder(line);
		while ((line = bufferedreader.readLine()) != null) {
			// System.out.println(line);
			sb.append(line);
			sb.append('\n');
		}
		// 使用exec执行不会等执行成功以后才返回,它会立即返回
		// 所以在某些情况下是很要命的(比如复制文件的时候)
		// 使用wairFor()可以等待命令执行完成以后才返回
		try {
			if (proc.waitFor() == 0) {
				return true;
			} else {
				Log.e("executeShellCmd", "exit value = " + proc.exitValue());
			}
		} catch (InterruptedException e) {
			Log.e("executeShellCmd", e.getMessage());
		}
		return false;
	}
	
	public void  destory() {
		dcpos = DCDevice.getInstance();
		if(dcpos != null) {
			dcpos.destroy();
			dcpos = null;
		}
	}
}
