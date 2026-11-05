# 源码对应与移植边界

固定基线：BH1SCW/MorseRunner `f5e96d3a08b1d60890975b345acdcf32aac37883`，Readme.txt 标识 1.71。

| 原模块 | 当前实现 | 保留的主要内容 |
| --- | --- | --- |
| Station.pas | core/engine.cpp：Station | 消息枚举、模板、号码缩写、发报缓冲与事件 |
| MyStn.pas | core/engine.cpp：Me | 分段发送、对方呼号实时修改、停发 |
| DxOper.pas | core/engine.cpp：Operator、matchCall | 动态规划呼号匹配、耐心与应答状态机 |
| DxStn.pas | core/engine.cpp：Dx | 技能、速度、号码、振幅、频偏、应答调度 |
| StnColl.pas | Engine::stations | 台站集合；删除改为标记后安全清理 |
| Contest.pas | Engine::audio、finished、started | IQ 噪声／混音、QSK、RIT、滤波切换、音频时钟 |
| QrmStn / QrnStn.pas | Qrm / Qrn | 干扰台、静电噪声和突发噪声 |
| Qsb.pas / QuickAvg.pas | dsp.hpp：Fading、QuickAverage | 三遍滤波的随机衰落增益与插值 |
| RndFunc.pas | dsp.hpp：Random | 正态、限幅高斯、Rayleigh、U 型、Poisson 公式 |
| MorseKey / MorseTbl.pas | dsp.hpp：Keyer | 字符表、点划单位、尾间隔、Blackman–Harris 包络 |
| MovAvg.pas | dsp.hpp：MovingAverage | 三遍递推滤波、归一化和交替滤波器复位 |
| VolumCtl.pas | dsp.hpp：Agc | 155/155 攻击／保持参数、对数包络窗口、非线性增益 |
| Mixers.pas | Engine::audio | 原载波周期取整行为、IQ 上变频 |
| Log.pas | Engine::save / stats / check / prefix | 交换号、重复判定、前缀正则、延迟核验、两种计分 |
| CallLst.pas | Engine::loadCalls / pickCall | 去重排序、均匀抽取、HST 不放回抽取；输入改为 SCP |
| ARRL.pas | CallsignInfo.java | 文本排序、反向前缀正则查找；沿用原 ARRL.LIST |
| Main.pas | MainActivity.java | Enter Sends Messages、快捷键与焦点；重建 Android 控件 |
| WinMM / SndOut.pas | AudioPlayer.java + bridge.cpp | Android 流式 AudioTrack，最终 PCM 转为 48 kHz |

## 明确的调整

1. 原采样率 11025 Hz、512 样本一块。核心保留该时基，JNI 边界线性重采样为 48000 Hz 输出；没有通过改变核心采样率改变延迟／噪声概率参数。
2. 原启动前 5 块返回 1 个样本以缩短等待。移植时返回 512 个静音样本，保持统一流式接口。这会影响启动阶段约 0.23 秒的输出行为。
3. 不复刻 Delphi 随机数位序。保留概率分布公式，采用可测试的 MT19937；同一 seed 只能在当前实现下复现。
4. 原 Pascal 的 self.Free 改为 dead 标记并在音频块边界清理，避免 C++ 遍历中的悬空引用。未采用跨线程共享裸指针；Java 同步 JNI 调用。
5. Agc 在对零取对数时增加数值下限；最终 PCM 转换限幅。控制设置、空呼号库和导入大小增加输入校验。
6. 核心与 Java 界面互不直接调用；界面每 200 ms 读取状态快照，声音时钟驱动台站，不使用 UI 定时器模拟点划。
7. 原 CallLst 的 DTA 输入替换为内置 SCP 文本库，训练所选候选集合因此以 SCP 内容为准。三套内置库均通过字符／长度校验，未丢弃有效呼号。
8. 最后 100 条日志用于屏幕显示，完整本轮记录仍用于评分与 CSV 导出。
9. 本台 WPM 与其他电台 WPM 独立设置。显式配置范围时，Dx 与 QRM 台按包含两端的整数均匀分布抽取 5–120 WPM；本台为 10–120 WPM。HST 同样遵守该范围，其操作员时限按对方自身 WPM 计算。这是有意偏离原版“其他电台随本台速度变化／HST 同速”规则的训练选项，不能将该设置下的 HST 结果视为原版同速条件的对照。范围两端都为零的旧核心配置仍沿用原规则。JNI 支持原 8 参数及新增的 10 参数（末两项为对方速度下限／上限）。
10. QSK 默认关闭，并在首次运行该版本时关闭一次旧配置的 QSK 位，保留其他效果位。之后允许手动开启并保存。仅调整默认配置和一次性迁移，QSK 的混音／包络算法未重写。

源码快照的头部 MPL 注释保留，移植文件注明衍生关系。基线未包含根 LICENSE 文件；根 LICENSE 取自原作者 VE3NEA/MorseRunner 的 MPL-2.0 文本，与各核心文件许可声明一致。

## 平台差异

在可运行 Windows 1.71 的环境中，对相同手工输入流程、无随机干扰的点划输出、典型呼号前缀、失败／重发场景做对照。随机场景比较速度、应答延迟和信号强度的分布，而非要求两个平台的随机样本完全相同。跨平台随机序列和逐样本波形不保证完全一致。
