# RootMyVivo

给锁定 Bootloader 的 vivo / iQOO 手机获取 root。不用解锁、不用刷 boot——装个 APK，点个按钮，等结果。

原理是 GhostLock（CVE-2026-43499，futex PI 的 use-after-free）加上在临时 root 之上装 KernelSU。

[Русский](README.md) · [English](README.en.md)

## 使用流程

1. 从 [Releases](https://github.com/zenyxx-xd/RootMyVivo/releases) 下载 APK 并安装
2. 启动 Shizuku（只需要一次，用来拿 shell 权限），或者开 ADB over TCP
3. 点「获取 ROOT」——应用自动匹配内核对应的 payload，下载并跑漏洞利用
4. 拿到 root 后自动装 KernelSU（四个管理器可选），重启前一直有效
5. 重启了？再点一次按钮，或者在设置里开自动恢复，漏洞利用会自己重跑

内核 panic 是可能出现的。这很正常：手机会自己重启，再试一次就行。是概率问题，不会变砖。

## 功能

- 免 root 检测机型、内核版本和 KMI
- 按完整内核字符串匹配 payload（同一个机型可能用不同内核构建，得分开对待）
- 应用内实时显示漏洞利用日志，保留最近几次的运行记录
- 手机重启后自动恢复 root（后台重跑漏洞利用）
- root 还活着时可以「重新运行漏洞利用」——root 在但 KernelSU 没装上的情况
- 应用内更新：每次运行后检查，卡片提示，带进度条下载，调系统安装器
- 「支持的设备」页面——整个 payload 目录，你的机型能用什么一目了然

## 支持的设备

完整列表在 [RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads)，应用每次启动都会拉最新的。

真机验证过的：

| 设备 | 内核 | 状态 |
|---|---|---|
| iQOO Neo 11 (PD2520) | 6.6.89 / 6.6.127 | 作者自测 |
| iQOO Z10 Turbo Pro (PD2453) | 6.6.89 | 用户反馈可用 |

已编译的移植（待真机验证）：iQOO 13（印度版）、iQOO Neo10 Pro、vivo X200、vivo X200 Pro。

内核必须没修 CVE-2026-43499：6.6 要低于 6.6.140，6.1 要低于 6.1.145。

## 传输方式

首次运行：Shizuku 无线 ADB 模式（配对，不需要电脑）。第一次拿到 root 后应用会固定 5555 端口的 ADB over TCP，之后就不再需要 Shizuku 了。

## 编译

```sh
./gradlew :app:assembleRelease
```

JDK 17+，SDK 35。签名用自己的 key，仓库里不放。

## 相关仓库

- [RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads) — payload 目录和二进制
- [RootMyVivo-Exploit](https://github.com/zenyxx-xd/RootMyVivo-Exploit) — 漏洞利用源码

## 免责声明

只用于自己的设备。变砖、丢数据、保修扯皮一概自理，风险自负。
