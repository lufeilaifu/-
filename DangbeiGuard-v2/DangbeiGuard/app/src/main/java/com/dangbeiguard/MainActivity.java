package com.dangbeiguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 主界面：引导用户开启无障碍服务，显示服务运行状态，
 * 并提供自定义拦截包名的入口（高级设置）。
 */
public class MainActivity extends Activity {

    private static final String TAG = "DangbeiGuard";
    private static final int REQ_ACCESSIBILITY = 1001;

    private TextView mStatusText;
    private Button mBtnToggle;
    private Button mBtnAddBlocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusText = findViewById(R.id.tv_status);
        mBtnToggle = findViewById(R.id.btn_toggle);
        mBtnAddBlocked = findViewById(R.id.btn_add_blocked);

        mBtnToggle.setOnClickListener(v -> openAccessibilitySettings());
        mBtnAddBlocked.setOnClickListener(v -> showAddBlockedDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    /**
     * 检查并刷新无障碍服务状态
     */
    private void refreshStatus() {
        boolean enabled = isAccessibilityServiceEnabled(this, HomeGuardService.class);
        if (enabled) {
            mStatusText.setText(R.string.hint_enabled);
            mStatusText.setTextColor(0xFF4CAF50); // 绿色
            mBtnToggle.setText("无障碍服务已开启（点击重新检查）");
        } else {
            mStatusText.setText(R.string.hint_not_enabled);
            mStatusText.setTextColor(0xFFFF5722); // 红色
            mBtnToggle.setText(R.string.btn_open_accessibility);
        }
    }

    /**
     * 跳转到系统无障碍设置页
     */
    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, REQ_ACCESSIBILITY);
            Toast.makeText(this, "请找到「当贝桌面守护」并开启", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to open accessibility settings", e);
            Toast.makeText(this, "无法打开设置，请手动前往：设置 > 无障碍", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 判断指定无障碍服务是否已启用
     */
    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabledServices == null) return false;

        String expectedId = context.getPackageName() + "/" + serviceClass.getName();
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getId().equals(expectedId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单展示添加自定义拦截包名的提示（TV 环境下可用 D-Pad 操作）
     * 实际使用时可通过 ADB 命令直接写入 SharedPreferences 更快捷：
     * adb shell settings put secure enabled_accessibility_services com.dangbeiguard/com.dangbeiguard.HomeGuardService
     */
    private void showAddBlockedDialog() {
        // TV 端简化处理：显示提示，指导用户用 ADB 添加
        Toast.makeText(this,
                "自定义拦截包名请通过 ADB 操作：\n" +
                "adb shell am start -a android.intent.action.MAIN " +
                "-n com.dangbeiguard/.MainActivity",
                Toast.LENGTH_LONG).show();
    }
}
