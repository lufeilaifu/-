# 当贝桌面守护（DangbeiGuard）

一个专为 **雷鸟/TCL 电视** 设计的 Android 无障碍守护应用，让当贝桌面拥有类似 `app.orchard.tv` 的 Home 键守护能力——当用户按下主页键时，如果系统试图唤起原厂灵控桌面，守护服务会自动将焦点切回当贝桌面。

## 适用场景

- 雷鸟电视（鹤7 24款等 MT9653 机芯机型）通过 ADB 安装了当贝桌面
- 原厂固件硬锁了默认桌面设置，`cmd package set-home-activity` 无效
- 需要一个守护服务拦截 Home 键行为，保持在当贝桌面

## 项目结构

```
DangbeiGuard/
├── app/
│   ├── build.gradle                 # 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml      # 应用清单，声明无障碍服务与自启权限
│       ├── java/com/dangbeiguard/
│       │   ├── HomeGuardService.java   # 核心无障碍服务：监听窗口变化、拦截系统桌面、切回当贝
│       │   ├── MainActivity.java       # 主界面：引导用户开启无障碍权限，显示状态
│       │   └── BootReceiver.java       # 开机广播：电视重启后唤醒服务
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml   # 主界面布局（适配 TV D-Pad 操作）
│           ├── xml/
│           │   └── accessibility_service_config.xml  # 无障碍服务配置
│           ├── values/
│           │   ├── strings.xml         # 文本资源
│           │   └── styles.xml          # 主题样式
│           └── drawable/
│               └── ic_tv.xml           # 占位图标
├── build.gradle                     # 项目级构建配置
└── settings.gradle                  # Gradle 项目设置
```

## 核心原理

1. **AccessibilityService**：注册监听 `TYPE_WINDOW_STATE_CHANGED` 事件，获取当前前台窗口包名。
2. **拦截判断**：如果包名匹配已知的系统桌面包名（灵控桌面等），触发拦截。
3. **强制切回**：通过 `startActivity` 发送带有 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 的 Intent，精确启动当贝桌面主 Activity。
4. **防抖保护**：800ms 冷却窗口 + 递归锁，防止启动过程中误触发无限循环。

## 预置拦截的系统桌面

| 包名 | 说明 |
|------|------|
| `com.tcl.android.launcher` | TCL/雷鸟 灵控桌面（常见） |
| `com.tcl.tvlauncher` | TCL 电视桌面 |
| `com.tcl.launcher` | TCL 桌面备用名 |
| `com.google.android.leanbacklauncher` | Android TV 原生桌面 |
| `com.android.tvlauncher` | AOSP TV Launcher |
| `com.google.android.tvlauncher` | Google TV Launcher |

> 如果你的电视灵控桌面包名不在上表中，可通过 ADB 命令添加自定义拦截（见下方）。

## 编译方法

### 方式一：Android Studio（推荐）
1. 在电脑上安装 [Android Studio](https://developer.android.com/studio)。
2. `File → Open`，选择本项目根目录。
3. 连接电视（确保电视已开启 ADB 调试），或选择 Build APK。
4. 点击 ▶️ Run，直接安装到电视。

### 方式二：命令行 Gradle
```bash
# 需要本地安装 JDK 17+ 与 Gradle
./gradlew assembleDebug
# APK 输出位置：app/build/outputs/apk/debug/app-debug.apk
```

## 安装与使用

### 1. 安装 APK
```bash
# 通过 ADB 安装到电视
adb install -r app-debug.apk
```

### 2. 开启无障碍服务（首次）
打开应用，按提示前往 **设置 → 无障碍 → 当贝桌面守护**，开启服务。也可以直接通过 ADB 授权：

```bash
adb shell settings put secure enabled_accessibility_services com.dangbeiguard/com.dangbeiguard.HomeGuardService
adb shell settings put secure accessibility_enabled 1
```

### 3. 自定义拦截包名（可选）
如果按下 Home 键后仍然跳回灵控桌面，说明其包名不在默认列表中。先用甲壳虫或 ADB 查看当前前台包名：

```bash
adb shell dumpsys activity activities | grep mFocusedWindow
```

找到灵控桌面的包名后，添加到 SharedPreferences：

```bash
# 假设灵控桌面包名为 com.example.launcher
# 需要 root 或使用 content provider 操作，更简单的方法是在代码中直接修改 DEFAULT_BLOCKED_PACKAGES 并重新编译
```

**更简单的做法**：直接编辑 `HomeGuardService.java` 中的 `DEFAULT_BLOCKED_PACKAGES` 集合，把你的包名加进去重新编译即可。

## 卸载

```bash
adb uninstall com.dangbeiguard
```

## 注意事项

- **无障碍权限**：Android 系统重启后，无障碍权限通常会被保留，但某些电视固件可能会自动关闭第三方无障碍服务，需检查。
- **防杀策略**：部分电视系统会 aggressively 杀后台服务。如遇到守护失效，尝试在电视 **设置 → 应用 → 当贝桌面守护 → 权限管理** 中允许后台运行、关闭电池优化。
- **MT9653 机芯**：此守护方案专为无法修改默认主页的固件设计，与之前 `app.orchard.tv` 的 `HomeGuardService` 原理一致，但更轻量、开源可定制。

## 许可

本项目仅供个人学习与电视桌面定制使用，按个人需求自由修改。
