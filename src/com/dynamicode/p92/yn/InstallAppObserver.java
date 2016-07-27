package com.dynamicode.p92.yn;

public interface InstallAppObserver {
	//安装成功
	public void onInstallFinished();
	//安装错误
	public void onInstallError(int errorCode);
}
