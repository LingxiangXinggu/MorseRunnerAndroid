# 继续构建与平板验收

## 0.1.9 ASCII 请求、设置滑块与 QSK 默认值

系统键盘字段统一英文 hintLocales 与 IME_FLAG_FORCE_ASCII，保留 NORMAL 文本／普通数字类别；新增反复 NR→清空→CALL 的 EditorInfo 回归，未引入密码类型、子类型切换权限或 IME 私有接口。用户不知道输入法品牌，需设备端继续确认回车后是否仍切换拼音。

设置中的本台 WPM、对方上下限、时长、活跃度、音调、带宽、侧音均保留手动输入并新增联动滑块；使用 SettingSlider 防止联动循环，手动空白／越界输入不会被强制替换，保存仍走原验证。

核心 qsk 默认 false，Java flags 缺省为 0；第一次运行本版本清除旧 flags 的 QSK 位，并写入 qskDefaultOffApplied 标志；其后手动重开 QSK 会保留。其他效果位不改。20 项 UI 测试及核心、Java、主机 JNI 检查通过。版本码 10，签名不变。

## 0.1.8 可选内置训练键盘

新增 keyboardMode 偏好（0 系统，1 内置，默认 0），首次引导和普通训练设置均可选择。TrainingKeyboard 为应用内面板：CALL 使用带数字行的 QWERTY，RST／NR 使用数字布局；编辑当前输入的光标／选区，按钮不夺取焦点。内置模式仅对主界面三个训练字段禁用系统软键盘，设置字段仍正常使用系统输入法；外接键盘保留。提供字段切换、删除／长按清空字段、全清、发送、收起及训练操作菜单。

收起、旧式返回和 Android 13+ 返回回调均支持关闭内置键盘。小高度时缩小键高并收起辅助区，训练操作可经内置面板的“更多”访问。14 项 UI 测试通过（原 9 项及新增 5 项），包括模式保存／取消／重开、选择范围编辑、数字字段、硬件字符输入、返回收起及手机／平板／短窗口渲染。版本码 9，原签名。实际设备输入法、返回手势和连续 CW 通联仍需真机验收。

## 0.1.7 窄屏双行快捷键

新增 ShortcutBar 按实际可用窗口宽度排布快捷键，宽屏 1×8，窄屏 2×4；移除快捷键区域的 HorizontalScrollView。测量考虑字体尺寸和按钮内边距，窗口宽度变化时重排原按钮，保留原点击监听与次序。版本码 8，沿用原签名。

## 0.1.6 普通文本键盘

用户实测 0.1.5 拉起了安全键盘。根因为公共呼号输入保留 TYPE_TEXT_VARIATION_VISIBLE_PASSWORD；现改为 TYPE_TEXT_VARIATION_NORMAL，保留 TEXT、英文提示、大写和无词语联想。应用未设置 FLAG_SECURE。键盘回归增加 EditorInfo 的 variation 为 NORMAL、无密码转换及主窗口无 FLAG_SECURE 的检查。版本码 7，签名不变。

## 0.1.5 呼号键盘

修复公共 input() 中为呼号设置 DigitsKeyListener 导致 EditorInfo 被覆盖为数字类型的问题。文本框保留文本 KeyListener，以字符过滤器限制 ASCII 呼号／问号／斜线，设置英文 IME hint；数字框仍使用数字 KeyListener，并显式过滤粘贴的非数字字符。版本码 6，沿用原签名。系统输入法对英文布局提示的实际响应仍需设备确认。

## 0.1.4 通联记录滚动

日志内容变化后，在文本及滚动容器布局完成时滚到底部，显示最新记录；不调用会移动输入焦点的 fullScroll。相同状态快照不会改变手动滚动的位置。最近 100 条窗口更新时即使控件高度不变，也会跟随最新记录。版本码 5，沿用原签名。

## 0.1.3 界面微调

导出 CSV 与训练设置集中于右上角“更多”菜单，记录区不再放置导出按钮。圆角统一由 14dp 改为 6dp，按钮关闭系统 elevation／状态动画。包名与签名保持不变，versionCode 4。

## 最新进展 · 2026-10-29 / 0.1.2

已完成浅色 UI、首次设置引导，以及本台 WPM 与其他电台 WPM 上下限分离。保留 0.1.1 包名与签名，versionCode 升为 3。首次升级会要求确认一次配置；旧配置预填，完成后不重复提示。速度范围对四种模式和 QRM 台均生效，HST 的变化见 PORTING.md 第 9 项。

已通过核心回归、新增范围测试、Java 设置测试、主机真实 JNI 调用、5 项 Robolectric 设置／布局测试和 release 构建。测试渲染图位于 `app/build/validation/ui`，属于 JVM 渲染而非设备截图。下一步仍为平板上的新界面、系统输入法、外接键盘、音频和首次设置实际验收。

## 最新进展 · 2026-10-28

后续已完成 0.1.1 原版图标及域名发布签名：包名 `com.asterdale.morserunner`，证书 `CN=asterdale.com, O=asterdale.com`，APK 为 `app/build/outputs/apk/release/app-release.apk`。构建／签名步骤见 `SIGNING.md`，图标来源见 `ICON.md`。签名私钥及密码单独交付，不包含在源码中；以后更新应继续使用该签名密钥。

本文件所述 Android 构建任务已完成：`bash scripts/build-android.sh` 成功生成 `app/build/outputs/apk/debug/app-debug.apk`，ABI 为 arm64-v8a。核心测试、Java 国家查询测试、APK 签名和 ZIP／ELF 16 KB 对齐核验通过，源码无需修复编译错误；原算法和数据未改动。

工具版本、校验值和证据见 `VALIDATION.md` 及 `build-logs/`。下一阶段从下方 Pad 7 Pro 验收步骤开始；设备运行和 Windows 1.71 对照尚未完成。

用户已同意以 BH1SCW/MorseRunner 1.71 为基线，保留上传呼号库；目标是小米 Pad 7 Pro、Android arm64-v8a、平板界面、系统软键盘及外接键盘。不要继续追索 1.75/1.76 EXE 的源码作为移植前置条件。

## 开发环境

本工程不依赖云端服务运行。云端 Codex、本地电脑或 GitHub Actions 只用于开发与构建。云环境若要构建，需要安装 JDK 17、SDK 35、Build Tools 35.0.0、NDK 28.0.13004108、CMake 3.22.1，并允许访问 Android／Gradle／Maven 官方依赖下载站点。

准备好 Android command-line tools 后，可执行：

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;28.0.13004108" "cmake;3.22.1"
bash scripts/test-core.sh
bash scripts/build-android.sh
```

代码目录应为仓库根目录（有 settings.gradle 的这一层）。没有 gradle-wrapper.jar；构建脚本会下载并校验固定版本 Gradle。

## 本次已完成的构建任务（保留原交接要求）

> 在当前工程上完成 Android Gradle 编译，修复实际编译错误并输出 arm64-v8a debug APK。保留 docs/PORTING.md 中记录的 1.71 算法对应关系与原呼号库字节。不要用简化的听写题逻辑替代多台站通联状态机。运行核心测试，增加任何修复实际需要的测试。不要把“主机测试通过”写成“安卓已验证”。构建完成后列出 APK 路径、构建工具版本、仍需真机验证的项目。不要自动创建外部仓库、推送或公开发布。

## Pad 7 Pro 验收步骤

1. 安装 APK，在设置中填写本台呼号。先用 Single Calls、30 WPM、全部干扰关闭。
2. 确认有 CW 音频；屏幕输入框能弹出系统输入法，输入呼号不被自动改词。用屏幕 Enter 完成一次呼号／交换号／TU 流程，核对 Verified 增加。
3. 接蓝牙、USB 或磁吸键盘，依次测试字母、数字、斜线、问号、F1–F8、Tab、Shift-Tab、Enter、Esc、Ctrl-W、PgUp/PgDn；记录被系统截获的组合键。
4. 横屏、竖屏、分屏、工作台窗口缩放；软键盘弹出后输入栏和 Enter 按钮应可见。旋转不应重新开始本轮训练。
5. 在 Pile-Up 中开启各项干扰，验证没有断音、尖峰失真或随时间漂移。分别测试扬声器、有线／USB 音频、蓝牙耳机；蓝牙延迟单独记录。
6. 切换到后台／其他 App，确认停止音频并保存本轮 CSV；回到 App 应显示已停止，不能假装比赛仍在连续运行。
7. 导出 CSV，确认总记录数和得分一致。导入一个 SCP，再重启训练，确认候选库按所选库读取。
8. 最后做长时间 WPX 和 HST，比较 Windows 1.71 的手工操作行为和统计趋势。此步骤通过前，不宣称完全复现原版。
