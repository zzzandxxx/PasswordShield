# 密码盾 Password Shield

一款**零依赖、纯本地**的安卓密码管理 App：安全生成复杂密码 + AES-256-GCM 加密保管，不联网、不上传、无任何第三方依赖。

- 安装包仅约 **80KB**（无广告、无追踪、无网络权限申请——连 INTERNET 权限都没有）
- 支持Android 8.0+（API 26），arm64 / x86 等所有架构（纯 Java 实现）

## 功能特性

### 🔐 密码生成
- 长度 6–64 可调
- 小写 / 大写 / 数字 / 特殊符号独立开关
- 可排除易混淆字符（`0 O 1 l I`）
- `SecureRandom` + **拒绝采样**保证均匀分布（无模偏差），每类选中字符至少出现一次后 Fisher-Yates 洗牌
- 实时熵（bits）与四档强度条显示

### 🗄️ 密码库
- 条目字段：标题、账号、密码、网址、备注
- 搜索、展开查看、一键复制账号/密码、密码显隐
- 条目上直接显示该密码的强度等级

### 🔒 安全设计（核心）
- 主密码 → **PBKDF2-HMAC-SHA256 迭代 25 万次** 派生 256 位密钥
- 整个密码库用 **AES-256-GCM**（认证加密）整体加密后写入应用私有目录
- 密码错误时 GCM 认证直接失败；密文任何一比特被篡改都会解密失败
- 主密码**不落盘、不可找回**（忘记 = 只能重置清库）
- 锁定时内存中的密钥与明文全部清除
- 复制到剪贴板的密码在系统剪贴板预览中自动遮蔽（Android 13+）

### ⏱️ 自动锁定
- 无操作 1/3/5/10 分钟自动锁定（可关）
- 可选「切到后台立即锁定」

### 💾 备份迁移
- 导出**加密备份**文件（仍是密文，可安全放网盘）
- 新设备导入备份 → 用原主密码解锁即完成迁移
- 支持更改主密码（全库重新加密 + 重新加盐）

## 技术栈与架构

**Android 全平台 API，零第三方依赖**——4 个 Java 类即全部逻辑：

```
app/src/main/java/com/passwordshield/
├── MainActivity.java  # 界面与交互（生成/密码库/设置/锁定，单 Activity）
├── PwGen.java         # 密码生成（SecureRandom 拒绝采样）+ 香农熵强度估算
├── Crypto.java        # PBKDF2 派生 + AES-256-GCM 加解密
└── VaultStore.java    # 密码库：整库加解密、增删改、备份导入导出
```

数据落盘格式（vault.dat，JSON）：

```json
{ "v": 1, "iter": 250000, "salt": "…", "iv": "…", "ct": "…", "updatedAt": 0 }
```

`ct` 为整库（条目 JSON 数组）的 AES-GCM 密文，Base64 编码；每次保存使用全新随机 IV。

安全模型详见 [docs/技术文档.md](docs/技术文档.md)，使用手册见 [docs/使用说明.md](docs/使用说明.md)。

## 构建

**方式一：一键脚本（Git Bash，无需 Gradle/Android Studio）**

```bash
bash build-apk.sh
```

脚本直接调用 Android SDK 的 aapt2 → javac → d8 → zipalign → apksigner，首次运行自动生成自签密钥。需本机具备 JDK 8+ 与 Android SDK（build-tools 34 / 30、platforms/android-34）。

**方式二：Android Studio**

直接打开项目根目录（Gradle 文件已就位，同步后 Run）。

> 仓库不含签名密钥。用同一台机器连续构建时 `build/release.keystore` 会被脚本复用，保持签名一致可覆盖升级安装。

## 下载安装

到 [Releases](../../releases) 页面下载最新 `PasswordShield-vX.X.apk`，传到手机点击安装（需允许"未知来源应用"）。

## 安全提醒

- **主密码务必牢记**：没有任何找回机制，忘记即数据永久不可恢复
- 卸载 App 会同时删除密码库，重要数据请定期导出加密备份
- 本项目按"个人自用"的安全标准设计，未做 SafeNet/强混淆等对抗逆向的加固；如需保管高价值密钥，请自行评估风险

## License

MIT
