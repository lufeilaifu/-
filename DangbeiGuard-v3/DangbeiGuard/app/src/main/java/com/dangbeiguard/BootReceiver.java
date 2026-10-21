package com.dangbeiguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启动广播接收器
 * 电视重启后，如果无障碍权限仍在，系统会自动重新绑定服务；
 * 此处额外做一次保活唤醒，确保服务尽快恢复。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "DangbeiGuard";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot completed, ensuring HomeGuardService is active");

            // 启动主 Activity 做前台保活引导（TV 系统通常允许）
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
    }
}
