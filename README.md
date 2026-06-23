# Vulcan Conventer Android APK

Vulcan Conventer Android APK 是手机端本地音频整理与私有媒体容器解包工具。它面向需要在 Android 设备上导入、整理、输出和播放本地音乐文件的场景，所有解析与文件处理都在设备本地完成。

## 核心能力

- 通过 Android 文件选择器导入本地音频文件。
- 授权后扫描系统媒体库并加入应用播放列表。
- 支持 `.ncm`、`.kgm` / `.vpr`、`.qmc` / `.mflac` / `.mgg` 的本地解包。
- 自动识别解包后的 MP3、FLAC、WAV 音频负载。
- 大文件导入、扫描、解包、输出均在后台线程执行。
- 私有容器解包采用流式写入，降低大文件导致内存峰值和闪退的风险。
- 支持播放、暂停、上一首、下一首、长按删除、清空列表。
- 支持从界面打开或定位输出目录。
- UI 为中文深色界面。

## 输出规则

- 解包出 MP3 时，输出 `.mp3` 文件。
- 解包出 FLAC 或 WAV 时，保留源格式输出为 `.flac` / `.wav`，不强制转 MP3。
- 文件名尽量保留原始歌名，仅替换 Android 文件系统不允许的字符。
- 输出路径会显示在歌曲行和底部播放信息中。
- 无法识别的负载会显示失败原因，不做伪转换。

默认输出目录位于应用外部私有音乐目录下的 `VulcanOutput`。

## 使用方式

1. 点击 `导入` 选择一个或多个音频文件。
2. 点击 `处理输出` 等待解包或复制完成。
3. 点击歌曲行播放已经输出的音频。
4. 点击 `打开输出` 定位输出目录。

如果提示权限不足，请重新导入文件或通过系统文件选择器授予读取权限。

## 构建

环境要求：

- JDK 17
- Android SDK platform 36 和 build-tools
- 可用的 Gradle，或本机存在 `F:\Gradle\gradle-9.6.0`

构建命令：

```powershell
cd F:\Codex-projects\Volcanic-MusicPlayer-Android
.\scripts\build-apk.ps1
```

构建输出：

```text
app\build\outputs\apk\debug\app-debug.apk
```

如果 Android SDK 没有全局配置，复制 `local.properties.example` 为 `local.properties` 并设置 `sdk.dir`。

## 说明

本项目仅用于本地个人媒体资产整理、兼容性测试和离线格式恢复。请只处理你拥有合法访问权的音频文件。
