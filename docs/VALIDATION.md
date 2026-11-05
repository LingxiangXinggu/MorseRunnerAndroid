# 检查记录 · 2026-10-31

## 0.1.9 输入法请求、数字滑块与默认 QSK

用户报告每次回车记录 QSO 后，系统输入法从英文变为拼音。应用侧新增 IME_FLAG_FORCE_ASCII，并对文本及数字输入提供一致的 Locale.ENGLISH 提示，保留普通文本／数字类型。没有使用密码类型、输入法私有接口或强行更改全局输入法子类型。用户不清楚所用输入法品牌，无法在本环境复现其真实 IME 行为；本次回归验证的是输入协议和焦点路径，不宣称已验证该设备的语言状态。

数字设置共 8 项添加 SettingSlider：本台速度、对方最低／最高速度、时长、活跃度、音调、带宽和侧音衰减。拖动同步文本，手动输入同步滑块；允许临时空白和越界数字而不覆盖文字，保存时仍拒绝非法值。手输 637 Hz、413 Hz 等精确值可正常保存。回归发现 SeekBar 初始 max=100 会把先设置的 min=300 截为 100，已调整为先 max 后 min 并复测通过。

QSK 默认 false／flags=0。首次启动本版本清除旧 flags 的 32 位一次（qskDefaultOffApplied），不改变其他效果位，之后手动重开 QSK 可跨启动保留。

本次验证：

- 共 20 项 Robolectric 测试通过：原设置／普通键盘／内置键盘／日志和快捷键回归，新增反复 NR→清空→CALL 的 ASCII 标记、英文 locale、NORMAL 类别检查，以及设置联动、精确值保存／重开、空白／越界验证、取消不保存、QSK 新装／升级／手动重开。
- 核心测试通过，新增 Config 默认 qsk=false 断言；CW、完整通联、干扰会话、HST、独立速度及原数据 SHA-256 回归通过。
- Java profile／国家查询测试和主机真实 JNI 音频／参数测试通过；显式 QSK 开启参数兼容保留。
- 最终 testDebugUnitTest、assembleRelease 与 lintVitalRelease 通过，版本 0.1.9-port171 / versionCode 10，原包名与签名不变，APK 签名及 ZIP／ELF 16 KB 对齐检查通过。

已查看设置滑块的原生图形测试渲染图；它不是实机截图。证据与校验值见 build-logs 下含 0.1.9 的日志及 XML。JNI 源码、呼号库和上游源码未改，核心仅修改 QSK 默认值；仍需用户设备确认实际系统 IME 语言切换和关闭 QSK 后的听感。

## 0.1.8 系统／内置训练键盘选择

首次引导与普通设置增加训练输入键盘选项，缺省系统键盘；不重置既有用户的设置或强制重新引导。内置键盘支持 QWERTY、数字行、数字布局、CALL／RST／NR 切换、选区替换、退格／长按清空字段、全清、ESM 发送及收起。只对训练输入框关闭 showSoftInputOnFocus，不改变其正常文本／数字输入类型，不设置密码或禁止截图标记，设置页仍使用系统键盘。

本次 14 项 Robolectric 测试通过（原 9 项 + 新增 TrainingKeyboardTest 5 项）：

- 首次选择保存、Activity 重开保持、设置字段仍支持系统键盘；旧版本默认系统键盘、模式切换和取消不保存。
- 字母／数字／斜线／问号、光标和选区替换、退格和长按清空、空字段退格、RST 默认值替换、全清及发送路由。
- 收起／重开保留当前字段、旧式返回不退出 Activity、内置模式下实体 A 键输入及普通文本类型保持。
- 400×850 手机、1000×800 平板、400×420 短窄窗口和 1000×380 短宽窗口中，内置键盘按键全部位于可用区域；数字布局同样通过。
- 既有设置、普通键盘类型、日志跟随底部及自适应快捷键回归继续通过。

已查看上述原生图形测试渲染图，字母／数字布局正常且有通联输入空间；它们不是设备截图。内置键盘展开时收起辅助区，短窗口还会收起主标题和状态，开始／结束训练可从内置键盘“更多”访问。Android 13+ 注册专用返回回调，收起及销毁时注销；较旧系统保留 onBackPressed 回退路径。

最终源码上 testDebugUnitTest、assembleRelease、lintVitalRelease 成功。版本 0.1.8-port171 / versionCode 9，沿用原签名，签名与 16 KB ZIP 对齐核验通过。核心、JNI、数据未改，本次未重复算法测试。日志、XML 和 APK 校验值见 build-logs 下含 0.1.8 的文件。

仍需真机确认真实返回手势、系统输入法切换、连续内置键盘抄报／发送和音频并行时的体验。JVM 编辑与布局测试不等同于完整设备通联验收。

## 0.1.7 手机双行快捷键

快捷键区域改用按实际窗口宽度测量的 ShortcutBar，窄屏 2×4、宽屏 1×8；移除该区域的横向滚动容器，按钮等宽，F1–F8 顺序、点击动作及实体键映射保持不变。窗口宽度改变时移动原按钮重新排布，不创建重复监听。

9 项 Robolectric 测试全部通过，新增回归覆盖 1000×800 宽屏、400×850 窄屏、400×420 紧凑窄屏及再次恢复宽屏，校验 8 个按钮均在可用区域内、没有横向滚动祖先、行数正确。已查看对应测试渲染图，窄屏两行、宽屏单行均正常；它们不是设备截图。

最终源码上的 testDebugUnitTest、assembleRelease 和 lintVitalRelease 通过。版本 0.1.7-port171 / versionCode 8，沿用原包名、图标和签名。签名与 16 KB ZIP 对齐核验通过；核心、JNI、数据不变，本次未重复算法测试。证据与 APK SHA-256 见 build-logs 下包含 0.1.7 的日志和 XML。真实系统键盘、分屏和旋转效果仍需设备确认。

## 0.1.6 去掉密码输入类型

用户实测 0.1.5 的呼号框拉起系统安全键盘并限制截图。此前只校验 TEXT 类别，遗漏了 TYPE_TEXT_VARIATION_VISIBLE_PASSWORD。现将公共呼号输入明确设为 TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_NORMAL，保留英文提示、自动大写和字符过滤；设置页及主页 CALL 同时修正，数字字段不变。

回归检查增加 EditorInfo 的 variation 必须为 NORMAL、无 PasswordTransformationMethod、Activity 窗口无 FLAG_SECURE。8 项 Robolectric 测试全通过，包含首次引导、普通设置和主页输入框。源码中不存在密码类型或 FLAG_SECURE 设置。发布构建与 lintVitalRelease 通过；版本 0.1.6-port171 / versionCode 7，沿用原签名。签名与 16 KB ZIP 对齐核验通过，相关日志和校验值位于 build-logs 下含 0.1.6 的文件。

这次变更不涉及核心、JNI 和数据，未重复算法测试。测试验证应用侧键盘请求和窗口标记，未替代用户设备上的系统输入法及截图实测。

## 0.1.5 呼号输入请求英文全键盘

根因：input() 先设置文本 inputType，再给呼号框设置 DigitsKeyListener；后者将最终输入法类型覆盖为 NUMBER，即使允许字符中包含英文字母也仍被识别成数字键盘。

修复：呼号框保留文本 KeyListener，使用 InputFilter 限定字母、数字、斜线和问号并自动大写，提供 Locale.ENGLISH 的 IME 提示。数字框继续使用数字 KeyListener，额外显式过滤粘贴内容中的非数字字符。

新增两项回归直接读取 onCreateInputConnection 的 EditorInfo：首次设置、普通设置和训练 CALL 均为 TEXT 且英文提示为 en；速度上下限、RST 和序号均为 NUMBER；呼号大写／字符过滤与数字粘贴过滤通过。合计 8 项 Robolectric 界面测试全通过，先前日志滚动和设置流程仍通过。

最终源码上 testDebugUnitTest、assembleRelease、lintVitalRelease 通过。版本 0.1.5-port171 / versionCode 6，沿用原包名与签名；签名及 16 KB ZIP 对齐检查通过。核心、JNI、数据未修改，本次未重跑算法测试。证据与 APK SHA-256 见 build-logs 下包含 0.1.5 的日志及测试结果 XML。

此项验证的是应用给输入法的正确类型与英文提示，未在用户设备安装第三方输入法进行实测；具体布局仍由系统输入法决定。

## 0.1.4 通联记录自动滚到底部

修复日志内容更新后未定位最新记录的问题：每次内容变化都触发布局，并在全局布局完成后滚至底部，避免文本或滚动容器布局覆盖滚动位置；使用 scrollTo，不移动输入焦点。相同的 200 ms 状态快照不会改变手动阅读旧记录的位置。

本次 6 项 Robolectric 测试全部通过，新增回归覆盖长列表底部可见、记录达到 100 条显示上限后等高窗口更新、输入焦点保留、相同内容不打断手动滚动及新会话清空回到顶部。已查看 `log-follow.png` 测试渲染图，最后一条记录可见、NR 输入保持焦点。渲染图不是设备截图。

最终源码上的 UI 测试、assembleRelease 与 lintVitalRelease 一次执行通过。版本为 0.1.4-port171 / versionCode 5，保留原包名和签名；APK 已通过签名及 16 KB ZIP 对齐核验。核心、JNI 和数据不变，本次不重复相关算法测试。日志及校验值见 `build-logs/` 下包含 `0.1.4` 的文件。仍需在设备上确认实际效果。

## 0.1.3 导出入口与圆角调整

按用户实机截图反馈，将“导出 CSV”移入顶部“更多”菜单，与“训练设置”集中放置；通联记录区只保留标题。控件圆角从 14dp 减小为 6dp，关闭按钮的系统 elevation 与状态动画，避免实机默认样式产生明显投影。

5 项 Robolectric 测试通过，包括菜单入口／设置导航及原首次设置流程；生成并检查 400×850 窄屏、1000×800 平板和紧凑窗口渲染图。渲染图不是实机截图。发布构建及 lintVitalRelease 通过，APK 签名、16 KB ZIP 对齐、版本与包名检查通过。包名及证书与 0.1.2 一致，versionCode 为 4，可覆盖安装。

本次没有修改核心、JNI、速度配置及数据文件，沿用 0.1.2 的相关测试结果，未重复核心测试。证据见 `build-logs/` 下包含 `0.1.3` 的文件。

APK SHA-256：`3a7b08da3be4e14020151da445f4e6191eef385d3e02041b9521bbe4e9f501bc`。

## 0.1.2 浅色界面、独立速度与首次设置

本次实现：浅色主题、蓝色主操作、卡片式得分／记录区、紧凑窗口自适应；独立本台 10–120 WPM 和其他电台 5–120 WPM 上下限；必须完成并校验的首次设置引导，支持从旧版预填配置、保存后不再重复提示，普通设置可取消而不写入。设置页的软键盘和物理按键不再触发训练快捷键。

| 验证 | 本次结果 |
| --- | --- |
| 核心测试 | 既有回归全部通过；新增四种模式的独立速度、固定速度 5／25／120、随机范围两端、QRM 范围、HST 应答时限、实际发报包络、非法范围拒绝测试通过 |
| Java | TrainingProfile 的呼号校验、独立速度、模式／库范围、JNI 参数映射通过；原国家查询测试通过 |
| 主机真实 JNI | g++ 共享库由 JDK 17 的 NativeEngine 实际调用：10 参数、音频渲染、本台调速、旧 8 参数兼容、非法参数／范围拒绝均通过。此项不是 Android 设备运行 |
| Robolectric | SDK 35，共 5 项，0 失败：首次强制设置／返回键、错误输入不保存、自定义库未导入不保存、模式／库／速度保存、再次启动不重复提示、旧版预填、普通设置取消，以及常规／紧凑窗口布局和首次引导渲染 |
| 布局修复 | 紧凑窗口测试曾发现发送按钮不可见，已改为测量子控件前按可用高度切换辅助区，复测 1000×380 输入／发送按钮可见 |
| Android 发布构建 | `assembleRelease`、`lintVitalRelease` 通过，49 个任务；Java／DEX、ARM64 JNI 链接与打包通过 |
| 更新兼容 | 包名 `com.asterdale.morserunner` 不变，versionName `0.1.2-port171`，versionCode 3；证书 SHA-256 与 0.1.1 完全相同 |
| APK 核验 | 发布包非 debuggable；v2 签名有效；ZIP 与 ELF PT_LOAD 16 KB 对齐检查通过；仅 arm64-v8a |
| 数据 | 三套呼号库 SHA-256 不变；APK 内的全部五个资产文件与源码逐字节一致 |

APK：`app/build/outputs/apk/release/app-release.apk`，1,437,054 字节。

SHA-256：`46cdc1dbd2149f8e13d07c5c93bb860fac8fbe050a9016a4032eee36ae3db845`。

工具链继续采用下方记录中的 JDK 17／Gradle 8.11.1／AGP 8.9.2／SDK 35／Build Tools 35.0.0／NDK r28／CMake 3.22.1。新增仅供测试的 JUnit 4.13.2、Robolectric 4.14.1，AndroidX 开关为其测试依赖启用，发布包不引入这些测试库。

测试命令：`bash scripts/test-core.sh`、`JAVA_HOME=... bash scripts/test-java.sh`、`JAVA_HOME=... bash scripts/test-jni.sh`、Gradle `:app:testDebugUnitTest`。构建命令仍为配置签名环境变量后的 `bash scripts/build-android.sh release`。

证据位于 `build-logs/` 下包含 `0.1.2` 的日志及测试结果 XML。Robolectric 原生图形渲染图输出到 `app/build/validation/ui/`；交付目录另附主界面、紧凑窗口、设置与首次引导预览。它们不是模拟器／平板截图，尚未验证真实系统键盘弹出、实际音频、设备旋转／分屏或安装覆盖过程，需在 Pad 7 Pro 验收。

算法变化见 PORTING.md 第 9 项。尤其 HST 现在也允许独立的对方速度范围，与原版固定同速条件不同。首次从 0.1.1 升级需确认一次预填设置；已保存设置后再次打开不重复引导。

## 0.1.1 原版图标与 asterdale.com 发布签名

用户要求使用原版图标，并提供域名 `asterdale.com`。已从原始 `Main.dfm` 提取 ICO，加入多密度和自适应启动图标；包名改为 `com.asterdale.morserunner`，版本 `0.1.1-port171` / versionCode 2；新增从环境变量读取发布签名的配置及 `build-android.sh release` 入口。核心、JNI、Java 运行逻辑和原数据文件保持不变。

实际验证：

- `bash scripts/build-android.sh release`：BUILD SUCCESSFUL，49 个任务执行；包括 release Java／DEX、ARM64 本地库及 lintVitalRelease。
- 缺少签名配置时，release 脚本以退出码 2 结束；不支持的构建类型也以退出码 2 结束。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 DN 为 `CN=asterdale.com, O=asterdale.com`；发布包未设置 debuggable 标志。
- APK 包名、版本、arm64-v8a 架构、Manifest 图标引用和打包后的 adaptive-icon 资源检查通过。
- `zipalign -c -P 16 -v 4` 通过，ELF PT_LOAD 段满足 16 KB 对齐。
- 原 ICO 与 `Main.dfm` 中的 Icon.Data 逐字节一致；APK 内三套 SCP、ARRL.LIST、wl_cty.dat 均与原资产一致。

发布 APK：`app/build/outputs/apk/release/app-release.apk`，1,436,074 字节。

APK SHA-256：`ab50f749203591340dcc17c29a5ec87080387570fd810a3b247288553b669cae`。

证书 SHA-256 指纹：`e742d4fc144f14a2c62f5d758dada4ff536d80f8fa8345a1b72375fb8046b3f2`。

工具链与下方 0.1.0 记录相同。本次图标／签名改动未涉及核心算法，沿用本次会话此前通过的核心和 Java 查询测试，未重复运行这些测试。证据见 `build-logs/release-icon-2026-10-28.log` 和 `build-logs/release-icon-verification-2026-10-28.log`。图标来源见 `ICON.md`，签名说明见 `SIGNING.md`。

私钥和密码文件单独交付，不打入源码或 APK。新版包名不同于先前 debug 包，作为新应用安装，不自动继承旧包数据。尚未进行模拟器或平板安装、图标在真实启动器中的显示或任何设备运行验收。

## 0.1.0 Android 构建与产物核验

已按 `CODEX_HANDOFF.md` 完成 ARM64 debug APK 构建。此次未出现项目编译错误，因此未修改 Java、C++、构建配置、移植算法或数据文件；仅更新交付文档并添加构建／核验日志。

| 项目 | 本次实际结果 |
| --- | --- |
| 核心回归 | `bash scripts/test-core.sh` 通过；包括完整音频时钟驱动 ESM 通联、计分、干扰和 HST 等既有测试 |
| SCP 数据 | 三套文件 SHA-256 与 DATA_MANIFEST.json 一致；WPX 50,263、WAPC 585、原库 59,942 条 |
| Java 国家查询 | JDK 17 编译 NativeEngine、CallsignInfo 及既有 CallsignInfoTest，测试通过 |
| Android 构建 | `bash scripts/build-android.sh`：BUILD SUCCESSFUL，36 个任务执行；Android Java 编译、DEX、NDK ARM64 JNI 链接与打包通过 |
| APK 签名 | Build Tools 35.0.0 `apksigner verify --verbose` 通过，使用 APK Signature Scheme v2 的 debug 签名 |
| APK 架构 | 仅含 `lib/arm64-v8a/libmorserunner.so`；ELF64 / AArch64；六个 NativeEngine JNI 入口均存在 |
| 16 KB 静态对齐 | `zipalign -c -P 16 -v 4` 通过；本地库全部 ELF PT_LOAD 段对齐值为 0x4000，偏移满足页对齐要求。这不是设备运行验证 |
| APK 内数据 | 三套 SCP、ARRL.LIST、wl_cty.dat 与源码中的对应资产逐字节一致 |

APK：`app/build/outputs/apk/debug/app-debug.apk`，1,917,171 字节。

SHA-256：`3279baf9de134ee52a80dd64043e3d45d61246b0fc5003c31e4c35ca03addb54`。

本次使用的版本：

| 工具 | 版本 |
| --- | --- |
| JDK | Eclipse Temurin 17.0.21+9 |
| Gradle / AGP | 8.11.1 / 8.9.2 |
| SDK Platform | API 35，revision 2，Android 15 |
| Build Tools | 35.0.0 |
| NDK | 28.0.13004108（r28，Clang 19.0.0） |
| CMake | 3.22.1 |
| Command-line Tools | 下载版本 13114758 |
| Platform Tools | 37.0.1（构建时自动安装；未用于成功安装或连接设备） |

环境处理：首次网络访问受执行沙箱限制，启用该命令的网络权限后恢复；新下载的 JDK 使用系统 Java 信任库 `/etc/ssl/certs/java/cacerts`，SDK Manager 和 Gradle 经环境代理获取官方依赖；Gradle 分发包通过官方 SHA-256 校验。缓存和 SDK 放置在 `/workspace/toolchains`，不打入源码包。未创建外部仓库、推送或公开发布。

构建日志中有 Android API 弃用提示及只读主目录下的 metrics 初始化提示，均未阻止构建。后续可在普通可写开发环境使用 README 中的标准构建命令。

本次环境的构建复现命令（路径只适用于当前工作区）：

```bash
JAVA_HOME=/workspace/toolchains/jdk17 \
ANDROID_HOME=/workspace/toolchains/android-sdk \
ANDROID_USER_HOME=/workspace/toolchains/android-user \
GRADLE_USER_HOME=/workspace/toolchains/gradle-user \
XDG_CACHE_HOME=/workspace/toolchains/cache \
bash scripts/build-android.sh
```

证据：`build-logs/android-2026-10-28.log`、`build-logs/apk-verification-2026-10-28.log`。

尚未安装到模拟器或平板，未验证实际音频、软键盘、外接键盘、旋转／分屏、后台停止与 CSV、SCP 导入或 16 KB 页设备运行。仍需按 CODEX_HANDOFF.md 的八步进行 Pad 7 Pro 验收，并完成 Windows 1.71 的行为／统计对照。本次未重跑下文历史记录中的 sanitizer 检查，不能将历史结果当作本次新增验证。

## 原源码包的历史检查记录

以下记录来自最初源码包；其中的环境限制描述针对当时的环境，Android 构建状态以上方最新记录为准。

交付物是**Android 重构源码初版**。本记录区分主机验证和未完成的安卓验收。

## 已通过

| 检查 | 实际执行／结果 |
| --- | --- |
| C++ 编译 | g++、C++17、-O2、-Wall -Wextra -Wpedantic -Werror，无告警 |
| CW 编码 | PARIS、CQ 与常用字符；5–120 WPM 包络边界／对齐／幅度检查 |
| 呼号解析 | 注释、CRLF、重复、大小写、非法行；空库被拒绝 |
| 呼号匹配 | 精确、部分、问号、过短、单字错误与不匹配 |
| 前缀 | BD6、K1、3D2 及带斜线呼号，按 1.71 的正则顺序 |
| 计分 | NIL、DUP、RST、NR、有效 QSO，Raw 与 Verified |
| 延迟核验 | TU 完成后，早先录入的 NIL 更新为有效 QSO |
| 完整通联 | 通过音频块时钟生成 Single Calls 台站，Enter 发呼号／交换号，再 Enter 发 TU 并记录，最终 Verified=1；该用例未强制修改远端操作员状态 |
| 电波效果 | 开启全部效果的 60 秒虚拟 WPX 会话，输出长度正确、样本有限且受 AGC 限制，按时结束 |
| HST | 4 台站、模式参数、独立计分入口 |
| 上传数据 | 三套 SCP 的 SHA-256 与上传原件一致；50,263 / 585 / 59,942 条均符合解析器字符及长度约束 |
| 内存／未定义行为 | 同组核心测试通过 AddressSanitizer + UndefinedBehaviorSanitizer，detect_leaks=0 |
| JNI | 使用 OpenJDK 17 的实际 jni.h / jni_md.h，g++ -fsyntax-only 与 -Werror 通过；这不是 Android ARM64 链接验证 |
| Java 无 Android 依赖部分 | NativeEngine.java、CallsignInfo.java 及国家查询测试由 JDK 17 编译；国家查询测试通过 |
| Java 界面／音频源码 | JDK 编译器的 parse 阶段语法检查通过；未完成 Android API 类型检查 |
| 配置 | AndroidManifest XML 解析、两个 shell 脚本 bash -n 通过 |

核心测试复跑：

```bash
bash scripts/test-core.sh
```

国家信息 Java 测试（标准 JDK 17）：

```bash
mkdir -p /tmp/mr-java
javac -d /tmp/mr-java app/src/main/java/org/morserunner/tablet/NativeEngine.java app/src/main/java/org/morserunner/tablet/CallsignInfo.java tests/java/org/morserunner/tablet/CallsignInfoTest.java
java -cp /tmp/mr-java org.morserunner.tablet.CallsignInfoTest app/src/main/assets/ARRL.LIST
```

## 环境限制与未完成项

- 当前主机有 Java 17 编译器模块，但无 Android SDK、NDK、Gradle。构建脚本明确退出并提示设置 ANDROID_HOME。
- 尝试连接 Android SDK 官方下载地址失败，因此没有安装工具链、编译 APK 或生成安装包。
- LeakSanitizer 无法读取本环境的 /proc 任务信息，在默认配置下报错。后续显式关闭泄漏检测，仅运行 AddressSanitizer 和 UndefinedBehaviorSanitizer；不能据此宣称通过了泄漏检测。
- 尚未进行 Android API 类型检查、AGP 配置解析、NDK ARM64 构建、16 KB page-size 运行验证、模拟器／平板安装测试。
- 尚未实测输入法、磁吸／蓝牙／USB 键盘、HyperOS 工作台／分屏、音频焦点及耳机路由。
- 未做 Windows 1.71 波形、随机分布、全部快捷键和所有竞赛分支的对照回归；不能将“核心测试通过”解读为完全等价。

后续按 `CODEX_HANDOFF.md` 进行构建与真机验收。APK 构建或真机出现的新问题应在该工程上修复并记录，而不是隐去这些验证边界。
