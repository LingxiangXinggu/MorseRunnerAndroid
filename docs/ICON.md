# 原版启动图标

图标取自工程内固定基线 `upstream/BH1SCW/Main.dfm` 的 `MainForm.Icon.Data`，不是另行绘制。基线：BH1SCW/MorseRunner 1.71，提交 `f5e96d3a08b1d60890975b345acdcf32aac37883`。

- 原始 ICO：`artwork/morserunner-original.ico`，1,078 字节，内含 32×32 和 16×16 图像。
- ICO SHA-256：`5c5a9388f560316cef71aedabcb7427ff1a022a31a3b0b58a058f04d89bb8a77`。
- PNG 转换源：`artwork/morserunner-original.png`，使用 32×32 帧并保留透明度。
- Android mdpi 至 xxxhdpi 图标通过 ImageMagick 最近邻缩放生成，保留原图的像素风格；自适应图标将原图居中置于前景画布，使用浅色背景，适应圆形及其他系统蒙版。
- 应用和圆形图标均引用 `@mipmap/ic_launcher`，Android 8.0 及以上使用 adaptive-icon。

沿用上游作品的版权与许可归属，图标不代表原作者为 Android 移植版本背书。
