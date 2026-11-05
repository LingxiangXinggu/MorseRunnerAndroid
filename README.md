# Morse Runner Android · 1.71 核心移植

面向 Android ARM64 平板的原生重构工程。基线由用户选定为 **BH1SCW/MorseRunner 1.71**，不再以还原上传 EXE 的后续修改为前提。

**状态：0.1.9 增加英文／ASCII 输入请求，音调、带宽等 8 项数字设置增加联动滑块，QSK 改为默认关闭。** 本次升级会关闭一次旧配置的 QSK 位，保留其他干扰选项；之后可以在高级设置中手动开启，后续启动不会反复覆盖该选择。滑块保留精确手输，空白或越界输入仍按原规则验证。ARM64 发布包沿用 `com.asterdale.morserunner` 与 asterdale.com 签名，可覆盖升级之前的发布版本。

CALL、RST、NR 及设置输入向系统输入法提供一致的英文提示和 IME_FLAG_FORCE_ASCII，普通文本／数字类型保持不变；回车记录后沿用原清空和 CALL 焦点路径，不通过密码类型强制键盘。中文输入法是否遵循 ASCII 请求仍需在实际设备确认。

升级后默认沿用系统键盘，在“更多 → 训练设置 → 训练输入键盘”中可切换到内置键盘。设置页始终使用系统键盘，外接键盘仍可输入。内置键盘展开时收起辅助信息和重复操作栏以保留输入空间；底部“更多”提供停发及开始／结束训练，“收起”或返回可关闭键盘。收起后点输入框或“键盘”可重新打开，并保留当前输入字段。它是应用内面板，不会替换设备的系统输入法。

右上角“更多”包含“训练设置”和“导出 CSV”；通联记录区只保留标题。菜单在紧凑窗口中同样可用。

首次安装或从 0.1.1 升级后，会显示一次设置引导：确认本台呼号、发报速度、其他电台最低／最高速度、训练模式、呼号库和每轮时长。完成后再启动训练，后续打开不重复提示。最低／最高相同可固定对方速度；PgUp / PgDn 只改变本台 WPM。高级选项收起在“高级设置”内。界面窗口变小时会收起辅助信息，优先保留输入和发送操作。

## 基线与数据

界面预览（测试环境渲染，非真机截图）：[内置训练键盘](docs/screenshots/training-keyboard.png) · [音调／带宽滑块](docs/screenshots/settings-sliders.png)。

- 仓库：https://github.com/BH1SCW/MorseRunner
- 固定提交：`f5e96d3a08b1d60890975b345acdcf32aac37883`
- 原始 Pascal 源码快照：`upstream/BH1SCW/`（源码、文本界面资源和项目文件；不包含 Windows EXE、OBJ 等二进制）
- C++ 核心：`core/engine.cpp`、`core/dsp.hpp`
- 安卓界面与音频：`app/src/main/java/org/morserunner/tablet/`
- 原作者：Alex Shovkoplyas / VE3NEA；分支贡献者包括 BG4FQD、BH1SCW。
- 移植源码采用 MPL-2.0；原有版权／许可注释保留。用户提供的数据文件来源单独记录，不将其重新声明为移植代码的许可。

三套 SCP 文件来自上传的 MorseRunner.rar，字节保持原样：WPX 50,263 条、WAPC 585 条、“原”库 59,942 条。`docs/DATA_MANIFEST.json` 记录 SHA-256。上传包中的 `wl_cty.dat` 也已保留。国家信息栏使用 1.71 自带的 `ARRL.LIST`，维持该版本的查找规则。

1.71 原版读取 MASTER.DTA。此版本改为读取 SCP 文本，继续采用“去重后均匀随机抽取”的行为；HST 抽取后从本轮候选库移除该呼号。这里的 WAPC 是数据库选择，**不代表新增了 WAPC 竞赛计分规则**。

## 已实现的移植范围

- Pile-Up、Single Calls、WPX、HST 四种训练模式及模式条件设置。
- 台站行为状态机、技能／耐心、应答延迟、部分呼号与问号匹配。
- CW 编码、Blackman–Harris 键控包络、号码缩写与 LIDS 发报错误。
- 原版 11025 Hz 内部音频、IQ 混音、三遍移动平均滤波、AGC、QRN、QRM、QSB、Flutter、QSK。
- 通联日志、DUP/NIL/RST/NR 检查、延迟核验、WPX 前缀计分、HST 呼号长度分、QSO/h。
- 平板界面、系统软键盘、外接键盘、F1–F8、Enter ESM、Esc、Tab、空格、PgUp/PgDn 和 RIT／带宽快捷键。
- SCP 导入、三套内置库切换、配置保存、最近一轮 CSV 自动保存和手动导出。
- 国家信息栏；横竖屏、窗口大小变化与软键盘避让代码。

移植为按源码逻辑重写，并非原 Pascal 代码直接重编译。随机数发生器改用可注入种子的 C++ MT19937，浮点运算与 Android 最终音频重采样也会造成差异；不承诺与 Windows EXE 逐样本、逐随机序列一致。详见 `docs/PORTING.md`。

## 构建 Android APK

需要能联网下载依赖的 Linux/macOS 开发环境或 Android Studio：

| 依赖 | 固定版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| SDK Platform / Build Tools | 35 / 35.0.0 |
| NDK | 28.0.13004108 |
| CMake | 3.22.1 |
| ABI | arm64-v8a |
| 最低系统 | Android 8.0 / API 26 |

先用 Android Studio SDK Manager 或 sdkmanager 安装上表依赖，设置 `JAVA_HOME`、`ANDROID_HOME`，然后：

```bash
bash scripts/test-core.sh
JAVA_HOME=/path/to/jdk17 bash scripts/test-java.sh
bash scripts/build-android.sh
```

构建脚本下载固定版本 Gradle，并使用官方发布的 SHA-256 校验。成功后 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。这是用于自行安装测试的 debug APK。

正式发布构建：按 `docs/SIGNING.md` 设置四个签名环境变量后，执行 `bash scripts/build-android.sh release`，输出为 `app/build/outputs/apk/release/app-release.apk`。签名密钥和密码不在源码包中。新版包名不同于最初的 debug 版本，将作为新应用安装。原版图标来源见 `docs/ICON.md`。

本仓库包含 `Android ARM64` workflow，推送到 main／master 或手动运行时，会先测试核心，再构建并上传 debug APK artifact。该测试包使用自动生成的 debug 签名，不能覆盖当前使用 asterdale.com 发布签名安装的版本；正式更新仍需自行保管的发布密钥。签名私钥和密码不上传到仓库。

## 使用

1. 设置本台呼号、速度、训练模式、时长和呼号库。
2. 点击“开始”。Pile-Up / WPX 下按 F1 或空呼号时按 Enter 发 CQ；Single / HST 自动产生呼叫台站。
3. 填呼号，Enter 发送对方呼号和交换号；抄到对方交换号后填 NR，再按 Enter 发 TU 并记入日志。
4. 点击输入框可调起系统输入法，也可以点击“键盘”；蓝牙／USB／磁吸键盘经 Android 标准按键事件输入。
5. 切到后台或丢失音频焦点会停止并保存本轮训练；旋转、分屏尺寸变化由同一 Activity 处理。进程被系统终止时不恢复进行中的音频状态。
6. “导出 CSV”调用系统文件选择器，进入选择器时也会触发上述停止策略。

## 尚未完成的验收与差异

- 已完成 Gradle 配置解析、Android API 类型检查、ARM64 链接和 APK 签名／16 KB 对齐核验；尚未完成 APK 安装及设备运行验证。
- 平板真实音频延迟、输入法遮挡、功能键被系统截获、分屏布局等，需 Pad 7 Pro 真机验收。
- 旧排行榜上传、旧成绩签名／LST 导入导出、WAV 录音、5 分钟柱状统计图、CallsFromKeyer 选项尚未移植。
- 系统输入法提交字符已经接入；软键盘是否展示 F 键取决于输入法，屏幕功能按钮可替代。
- 核心测试证实若干算法与流程能够运行，尚未做 Windows 1.71 录音／统计分布的对照验收。

检查记录见 `docs/VALIDATION.md`；继续构建和真机收尾的任务说明见 `docs/CODEX_HANDOFF.md`。
