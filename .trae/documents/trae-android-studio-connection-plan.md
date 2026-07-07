# Trae 连通 Android Studio 最简配置方案

## 一、当前项目状态

| 项目 | 值 |
|------|-----|
| **项目路径** | `D:\Android-APP-me\apllicationUseTime` |
| **Android SDK** | `D:\AndroidSdk`（已通过 `local.properties` 确认） |
| **adb 路径** | `D:\AndroidSdk\platform-tools` |
| **包名** | `net.android.apllicationusetime` |
| **Gradle Wrapper** | `gradlew.bat`（已存在） |
| **构建系统** | AGP 9.2.1 + Kotlin 2.2.10 |

## 二、执行步骤

### 步骤 1：验证/配置 adb 在终端 PATH

当前已在 Trae 终端中，直接验证 adb 是否可用：

```bash
adb version
```

- **成功** → 输出版本号，跳到步骤 2
- **失败（"不是内部命令"）** → 说明 PATH 未包含 `D:\AndroidSdk\platform-tools`，需手动配置：
  - 打开 Trae 设置 → Terminal → 环境变量 PATH 追加 `D:\AndroidSdk\platform-tools`
  - 完全重启 Trae 后重新验证

### 步骤 2：检查模拟器连接

```bash
adb devices
```

- 输出含 `emulator-5554 device` → 模拟器已连接，跳到步骤 3
- 输出 `List of devices attached`（空） → 模拟器未运行或 adb 未识别：
  ```bash
  adb kill-server && adb start-server
  adb devices
  ```

### 步骤 3：构建项目 APK

```bash
.\gradlew.bat :app:assembleDebug
```

目标产物路径：`app/build/outputs/apk/debug/app-debug.apk`

### 步骤 4：安装 APK 到模拟器

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 5：启动应用

```bash
adb shell am start -n net.android.apllicationusetime/.MainActivity
```

### 步骤 6：查看 logcat 日志（可选）

```bash
adb logcat -s AndroidRuntime:E
```

## 三、互通方案速查

### 模拟器 App 访问 Trae 本机服务

```bash
adb reverse tcp:<端口> tcp:<端口>
# 模拟器内访问: http://127.0.0.1:<端口>
```

### Trae 终端访问模拟器内服务

模拟器内置固定域名 `10.0.2.2` 指向宿主机：
```bash
curl http://10.0.2.2:<端口>
```

## 四、故障排查

| 症状 | 修复 |
|------|------|
| `adb` 不是内部命令 | 确认 Trae Terminal PATH 包含 `D:\AndroidSdk\platform-tools`，重启 Trae |
| `adb devices` 无设备 | `adb kill-server && adb start-server`，确认模拟器已启动 |
| connection refused | 确认服务已启动，Windows 防火墙放行对应端口 |
| 构建失败 | 检查 `local.properties` 中 `sdk.dir` 是否为 `D:\\AndroidSdk` |
