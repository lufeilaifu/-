package com.dangbeiguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 当贝桌面守护服务
 * 核心逻辑：监听窗口状态变化，当检测到系统桌面（如灵控桌面）被唤起时，
 * 强制将 Activity 栈切回当贝桌面。
 */
public class HomeGuardService extends AccessibilityService {

    private static final String TAG = "DangbeiGuard";

    // 当贝桌面的包名与主 Activity
    private static final String DANGBEI_PACKAGE = "com.dangbei.tvlauncher";
    private static final String DANGBEI_MAIN_ACTIVITY = "com.dangbei.launcher.ui.main.MainActivity";

    // 默认需要拦截的系统桌面包名（灵控桌面 / 系统原生桌面等）
    // 用户可通过 ADB 或界面添加更多包名到 SharedPreferences
    private static final Set<String> DEFAULT_BLOCKED_PACKAGES = new HashSet<>(Arrays.asList(
        "com.tcl.android.launcher",     // TCL/雷鸟 灵控桌面常见包名
        "com.tcl.tvlauncher",           // TCL 电视桌面
        "com.tcl.launcher",             // TCL 桌面备用包名
        "com.google.android.leanbacklauncher", // Android TV 原生桌面
        "com.android.tvlauncher",       // AOSP TV Launcher
        "com.google.android.tvlauncher" // Google TV Launcher
    ));

    // 防重复跳转：最近一次的拦截时间戳
    private long mLastInterceptTime = 0;
    private static final long INTERCEPT_COOLDOWN_MS = 800;

    // 防递归标志：防止在启动当贝桌面的过程中再次触发拦截
    private boolean mIsLaunching = false;

    private Handler mHandler;
    private SharedPreferences mPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        mPrefs = getSharedPreferences("guard_config", Context.MODE_PRIVATE);
        Log.i(TAG, "HomeGuardService created");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 100;
            setServiceInfo(info);
        }
        Log.i(TAG, "HomeGuardService connected, watching for launcher switches");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // 只处理窗口状态变化事件
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        // 获取当前窗口的包名
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String currentPackage = pkgSeq.toString();

        // 如果已经在当贝桌面，无需处理
        if (DANGBEI_PACKAGE.equals(currentPackage)) {
            return;
        }

        // 检查当前包名是否在拦截列表中
        if (!shouldIntercept(currentPackage)) {
            return;
        }

        // 防重复跳转检查
        long now = System.currentTimeMillis();
        if (now - mLastInterceptTime < INTERCEPT_COOLDOWN_MS) {
            return;
        }

        // 防递归检查
        if (mIsLaunching) {
            return;
        }

        Log.i(TAG, "Intercepted system launcher: " + currentPackage + ", switching to Dangbei");
        mLastInterceptTime = now;
        mIsLaunching = true;

        // 切回当贝桌面
        launchDangbeiLauncher();

        // 500ms 后解除递归锁
        mHandler.postDelayed(() -> mIsLaunching = false, 500);
    }

    /**
     * 判断指定包名是否需要拦截
     */
    private boolean shouldIntercept(String packageName) {
        // 先检查默认列表
        if (DEFAULT_BLOCKED_PACKAGES.contains(packageName)) {
            return true;
        }

        // 再检查用户自定义列表
        Set<String> customBlocks = mPrefs.getStringSet("custom_blocked_packages", new HashSet<>());
        if (customBlocks.contains(packageName)) {
            return true;
        }

        // 启发式规则：如果包名包含 launcher 且不是当贝桌面，也拦截
        // 此规则可选，用户可通过设置关闭
        boolean heuristicEnabled = mPrefs.getBoolean("heuristic_block", true);
        if (heuristicEnabled && packageName.toLowerCase().contains("launcher")) {
            // 排除一些非桌面应用可能误包含 launcher 字样
            if (!packageName.contains("wallpaper") && !packageName.contains("theme")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 强制启动当贝桌面主 Activity
     */
    private void launchDangbeiLauncher() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setComponent(new ComponentName(DANGBEI_PACKAGE, DANGBEI_MAIN_ACTIVITY));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            Log.i(TAG, "Launched Dangbei Launcher");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Dangbei Launcher, fallback to package intent", e);
            // 降级方案：直接按包名启动
            fallbackLaunch();
        }
    }

    /**
     * 降级启动：当精确 Component 启动失败时，尝试按包名启动
     */
    private void fallbackLaunch() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(DANGBEI_PACKAGE);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback launch also failed", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "HomeGuardService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "HomeGuardService destroyed");
    }
}
