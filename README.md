# KeyBox — 个人密码管理器（Android）

本地优先、端到端加密的个人密码管理器。只需记住一个主密码，安全管理所有账号、密码、2FA 及敏感信息。

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.5-blue)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-orange)](LICENSE)

## ✨ 功能特性

### 核心功能
- 🔐 单一主密码解锁，全库加密存储
- 📝 账号条目的增删改查、搜索、收藏
- 🏷️ 分类 + 标签双重组织
- 🗑️ 回收站（30 天软删除）+ 密码历史

### 安全能力
- 🔑 **Argon2id** KDF + **DEK/KEK 分层密钥** + **AES-256-GCM** 加密
- 👆 指纹/面部识别解锁（Keystore 绑定，enrollment 变更自动失效）
- ⏱️ 后台 / 超时自动锁定
- 📋 剪贴板自动清除（30 秒）
- 🚫 截图/最近任务保护（FLAG_SECURE）
- 📵 **零网络权限**（无 `INTERNET`），完全离线

### 进阶功能
- 🔄 **Android Autofill**：自动填充 + 保存新密码 + 域名匹配
- 🔢 **TOTP（2FA）**：内置双因素认证动态验证码
- 🔎 **安全中心**：弱密码 / 重复密码 / 长期未修改检查（纯本地）
- 🎲 **密码生成器**：随机密码 + 密码短语两种模式
- 💾 **加密备份**：`.kbx` 格式导出/导入 + CSV 迁移

## 🏗️ 架构

```
app/src/main/java/com/keybox/app/
├── data/
│   ├── db/           Room 实体 + DAO（PasswordItem/Category/Tag/PasswordHistory）
│   ├── crypto/       Argon2id KDF + AES-256-GCM + DEK/KEK 分层密钥
│   ├── backup/       .kbx 加密备份格式 + CSV 导入
│   └── repository/   仓库层（业务逻辑 + 加解密）
├── domain/           密码生成器 / 强度检测 / TOTP / 安全分析
├── autofill/         AutofillService + 跨进程 DEK 存储
├── security/         生物识别 / 剪贴板清理
└── ui/               Compose 页面（锁屏/首页/详情/编辑/回收站/设置/备份/安全中心）
```

## 🔐 安全设计

### 密钥层级

```
主密码 --Argon2id--> KEK --> (解密) DEK --> AES-256-GCM --> 敏感字段
```

- **DEK**（Data Encryption Key）：随机 256-bit 密钥，加密所有数据，明文仅驻留内存，锁定即清零
- **KEK**（Key Encryption Key）：由主密码 + 独立随机 salt 经 Argon2id 派生，仅用于加密/解密 DEK
- **修改主密码** = 用新 KEK 重新 wrap DEK，无需重加密全部数据

### 安全基线

- `android:allowBackup="false"` —— 禁止系统备份绕过加密
- **无 `INTERNET` 权限** —— 完全离线，可验证的隐私承诺
- 每条记录独立 96-bit nonce（GCM），防 nonce 重用
- 备份使用更强 KDF 参数（2~3 秒成本）+ 独立随机 salt
- 生物识别仅作快捷解锁，`setInvalidatedByBiometricEnrollment(true)` 保证新增指纹后强制回退主密码
- 解锁失败指数退避、内存密钥主动清零

## 🧪 测试

31 项单元测试全部通过，覆盖：

- **加密核心**（10 项）：AES-GCM 往返 / 篡改检测 / 错误密钥拒绝、Argon2id 派生确定性 / 盐敏感性、端到端 DEK 封装
- **密码生成器**（8 项）：长度 / 字符集 / 随机性 / 易混淆排除、强度检测
- **安全分析**（5 项）：弱密码 / 重复密码 / 长期未修改检测
- **TOTP**（8 项）：含 **RFC 6238 附录 B 官方测试向量**验证

```bash
./gradlew testDebugUnitTest
```

## 🚀 构建

### 环境要求

- JDK 17+（本项目使用 JDK 21）
- Android SDK 34

### Debug 包

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 19MB）
```

### Release 包（签名 + 混淆）

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（约 2.8MB）
```

> 签名信息从 `keystore.properties` 读取（已加入 `.gitignore`，不纳入版本控制）。
> 首次构建需自行创建 `keystore.properties` 和 keystore 文件。

### 网络镜像说明

本项目 `settings.gradle.kts` 使用阿里云 Maven 镜像，避免直连 `dl.google.com` /
`mavenCentral` 时因网络环境导致的大文件 TLS 握手失败问题。

## 📄 许可

MIT License

## 📖 相关文档

- 设计文档：`KeyBox_个人密码管理器设计文档_v0.2.md`
- 用户使用说明：`docs/使用说明.md`
