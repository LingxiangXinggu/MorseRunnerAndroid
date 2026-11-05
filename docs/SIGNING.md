# 发布构建与签名

应用包名为 `com.asterdale.morserunner`，Java／JNI 命名空间为 `org.morserunner.tablet`。

## Debug 构建

```bash
bash scripts/build-android.sh
```

输出为 `app/build/outputs/apk/debug/app-debug.apk`。debug 签名用于开发测试，与发布签名不同。

## Release 构建

通过环境变量提供签名配置：

| 变量 | 含义 |
| --- | --- |
| MR_KEYSTORE | 本机密钥库的绝对路径 |
| MR_STORE_PASSWORD | 密钥库密码 |
| MR_KEY_ALIAS | 密钥别名 |
| MR_KEY_PASSWORD | 私钥密码 |

```bash
bash scripts/build-android.sh release
```

输出为 `app/build/outputs/apk/release/app-release.apk`。脚本在缺少签名变量时退出。Android Studio 也可通过 **Generate Signed Bundle / APK** 配置密钥库并选择 release 构建。

发布更新需要保持应用包名和签名密钥一致，并按发布计划增加 `app/build.gradle` 中的 versionCode／versionName。密钥库和密码由发布者保管，不提交到 Git 或分发到 APK 中。

## 官方发布证书

公开证书为 [asterdale-morserunner-release.pem](signing/asterdale-morserunner-release.pem)。

- Subject：`CN=asterdale.com, O=asterdale.com`
- RSA 4096 位，SHA256withRSA
- SHA-256 指纹：`e742d4fc144f14a2c62f5d758dada4ff536d80f8fa8345a1b72375fb8046b3f2`

公开证书用于核验发布者，不包含签名私钥。它是 Android 应用自签名证书，域名作为标识，不依赖网站 HTTPS 证书或联网验证。
