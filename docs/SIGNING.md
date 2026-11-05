# asterdale.com 发布签名

此版本使用包名 `com.asterdale.morserunner`，Java／JNI 命名空间仍为 `org.morserunner.tablet`。当前版本为 `0.1.9-port171`，versionCode 为 10；沿用 0.1.1 的同一签名密钥，可覆盖升级此前发布版本。

公开证书：`signing/asterdale-morserunner-release.pem`。证书 SHA-256 指纹：`e742d4fc144f14a2c62f5d758dada4ff536d80f8fa8345a1b72375fb8046b3f2`。公开证书可随源码分发；它不包含签名私钥。

包名来自反写域名。签名证书 Subject 为 `CN=asterdale.com, O=asterdale.com`，alias 为 `morserunner`；使用 RSA 4096 位密钥及 SHA256withRSA。Android 应用发布采用自签名证书，此处填写域名是身份标识，不等同于域名验证或网站 HTTPS 证书，也不要求运行时连接该域名。

本次生成的 PKCS#12 密钥库和密码文件分别提供，均不进入源码包或 APK。密钥库文件为 `asterdale-morserunner-release.p12`，密钥库密码与私钥密码相同；密码在单独的 `keystore-password.txt` 中。请将这两个文件保存到自己的安全备份中，不要随源码公开。以后发布可覆盖安装的更新需要使用同一包名和签名密钥，并增加 versionCode。

此前的 debug 包名为 `org.morserunner.tablet`。此版本是另一个应用，可并存安装，不自动迁移旧包的配置、导入库或本地日志。

## 构建

默认命令仍生成 debug APK：

```bash
bash scripts/build-android.sh
```

发布构建读取以下环境变量，源码不保存密码：

| 变量 | 含义 |
| --- | --- |
| MR_KEYSTORE | 本机 PKCS#12 密钥库的绝对路径 |
| MR_STORE_PASSWORD | 密钥库密码 |
| MR_KEY_ALIAS | `morserunner` |
| MR_KEY_PASSWORD | 私钥密码，与本次密钥库密码相同 |

设置上述变量后执行 `bash scripts/build-android.sh release`，输出为 `app/build/outputs/apk/release/app-release.apk`。缺少任一签名变量时，脚本会退出，不会交付未签名的 release APK。

也可在 Android Studio 的 Generate Signed Bundle / APK 中选择该密钥库和 alias，密码填入上述文件中的值。配置好 README 指定的工具链后选择 release 构建即可。
