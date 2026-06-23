# Volcanic Android APK

这是 Volcanic 的独立 Android APK 工程，不依赖也不改动桌面 Electron 项目。

## 当前能力

- 通过 Android 文件选择器导入本地音频。
- 授权后扫描本机媒体库。
- 导入后先复制到应用私有输入目录，避免外部 URI 权限失效导致无法读取。
- 本地解包 `.ncm`、`.kgm` / `.vpr`、`.qmc` 私有容器。
- 自动识别解包后的 MP3、FLAC、WAV 负载。
- 大文件导入、扫描、解包、输出都在后台线程执行，避免选择文件后卡死或闪退。
- 播放列表保存在应用私有目录。
- 支持播放、暂停、上一首、下一首、删除、清空。
- UI 使用中文深色界面。

## 输出规则

- 解包出 MP3 时，输出 `.mp3` 并用于播放。
- 解包出 FLAC/WAV 时，保留源格式输出为 `.flac` / `.wav`，不强制转 MP3。
- 输出文件名尽量保留原始歌名，只替换 Android 文件系统不允许的字符。
- 输出路径会显示在歌曲行和底部播放器详情中。
- 无法识别的负载会明确显示失败，不做伪转换。

## 构建

要求：

- JDK 17
- Android SDK platform 36 和 build-tools
- 可用的 Gradle，或本机存在 `F:\Gradle\gradle-9.6.0`

命令：

```powershell
cd F:\Codex-projects\Volcanic-MusicPlayer-Android
.\scripts\build-apk.ps1
```

输出：

```text
app\build\outputs\apk\debug\app-debug.apk
```

如果 Android SDK 没有全局配置，复制 `local.properties.example` 为 `local.properties` 并设置 `sdk.dir`。
