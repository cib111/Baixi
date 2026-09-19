# 白析（Baixi）

网盘分享链接解析与高速下载的 Android 应用。粘贴分享链接，即可浏览分享内容并直接多线程下载到本地。

> **官方仓库：https://github.com/cib111/Baixi** —— 源码、版本发布与问题反馈都在这里。
> 应用内「设置 → 关于 → 检查更新」会从本仓库的 Releases 获取新版本。

## 功能特性

- **分享链接解析**：识别夸克 / UC / 迅雷 / 百度 / 139 / 123 分享链接，自动匹配提取码，支持一次粘贴多条链接批量解析
- **高速下载**：Range 分片并发 + 断点续传，任务保存请求头与固定分片规划（`plan.txt`），默认 32 线程，可 1~512 调节
- **分平台线程数**：夸克 / UC / 百度 / 139 / 123 可单独设置，迅雷自动限制在 8 线程以内
- **弹性分段**：按字节顺序分配 4MB 块，物理相邻，缓解中后段掉速
- **临时转存清理**：百度 / 迅雷取链后立即清理；夸克等保留到下载完成或删除任务后清理
- **登录方式**：夸克 / UC / 百度 / 139 用 WebView Cookie；迅雷用密码或短信；123 用账号密码换 JWT
- **认证备份**：用户口令派生密钥（PBKDF2），AES-GCM 加密导出/导入 Cookie 与 JWT
- **应用内更新**：启动自动检查（可关）+ 手动检查，从 GitHub Releases 下载并自动拉起安装
- **已下载文件管理**：搜索 / 按类型筛选 / 按时间·名称·大小排序 / 图片视频缩略图 / 分享 / 重命名 / 批量删除
- **直链下载**：任意 HTTP/HTTPS 直链多线程下载，m3u8（HLS）自动识别并合并
- **文件预览**：图片（缩放/双击）、视频（全屏）、音频、PDF（逐页渲染 + 翻页）、文本（自动分页，UTF-8/GBK 自动识别）
- **下载统计**：今日/累计流量与完成数 + 实时速度曲线；单任务优先级与线程数可调
- **通知栏控制**：进度、速度、全部暂停 / 全部继续；下载完成通知可直接打开或安装
- **网络策略**：流量网络下询问 / 直接下载 / 等待 WiFi 三选一；限速（不限速或自定义 KB/s）
- **断点续传加固**：校验 ETag / Last-Modified，文件在服务端变化时自动放弃旧分片重下
- **启动恢复**：应用被系统杀掉后，下次启动可自动恢复未完成任务
- **深色模式与主题**：跟随系统 / 浅色 / 深色，动态取色（Android 12+）或自定义种子色

## 支持平台

- 夸克网盘
- UC 网盘
- 迅雷网盘
- 百度网盘
- 139 网盘（和彩云）
- 123 云盘

> **不建议用百度网盘，可能导致账号被风控！！！**

## 截图

| 解析直链 | 分享解析 | 下载管理 |
|:---:|:---:|:---:|
| ![解析输入](images/Link.jpg) | ![文件列表](images/Parsing.jpg) | ![下载管理](images/Download.jpg) |

| 网盘登录 | 设置 | 关于 |
|:---:|:---:|:---:|
| ![网盘登录](images/Login.jpg) | ![设置](images/Setting.jpg) | ![关于](images/about.jpg) |

## 下载安装

从 [Releases](https://github.com/cib111/Baixi/releases) 下载最新的 `Baixi-vX.Y.apk` 直接安装。

- 系统要求：**Android 6.0（API 23）** 及以上
- 正式包与调试包签名不同，无法互相覆盖安装；覆盖更新必须使用**同一个签名密钥**，否则系统会提示「应用未安装」

## 应用内更新

- **启动自动检查**：默认开启，网络异常静默失败；发现新版本才弹窗，可「跳过此版本」
- **手动检查**：设置 → 关于 → 检查更新，结果会有明确提示（已是最新 / 失败原因）
- **实现**：读取本仓库 `releases/latest` 的 `tag_name` 与本地 `versionName` 比较（语义化比较，`1.10 > 1.9`），取其中 `*.apk` 资产的下载直链；仓库是公开的，**走匿名 GitHub API，APK 内不含任何 token**
- **下载安装**：复用应用自身的多线程下载器（下载页可见进度、通知栏有进度），完成后自动拉起系统安装界面
- **更换更新源**：修改 `app/src/main/kotlin/com/baixi/app/data/update/UpdateRepository.kt` 中的 `OWNER` 与 `REPO` 两个常量即可

## 从源码构建

环境要求：**JDK 17**、Android SDK（`compileSdk 36`、`targetSdk 34`、`minSdk 23`）。

```bash
# 调试包
./gradlew assembleDebug

# 正式包（未签名，需自行签名后再发布）
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/`。发布流程见 [CHANGELOG.md](CHANGELOG.md) 与 `.github/workflows/release.yml`：

1. 修改 `app/build.gradle.kts` 中的 `versionCode`（递增）与 `versionName`
2. 提交并推送，打 tag：`git tag v1.3 && git push origin v1.3`
3. GitHub Actions 自动构建并把 APK 附到 Release（也可本地构建后手动上传）
4. **Release 的 tag 必须与 `versionName` 一致**（`v1.3` ↔ `1.3`），否则应用内更新会一直提示有新版本

## 项目结构

```
app/src/main/kotlin/com/baixi/app/
├── MainActivity.kt / BaixiApp.kt   # 入口与应用级单例
├── data/
│   ├── network/       # 各网盘 API（夸克/UC/迅雷/百度/139/123）、分享链接解析、HTTP 客户端
│   ├── repository/    # 各平台账号与取链逻辑、临时转存清理
│   ├── download/      # 多线程分片下载引擎、前台服务、断点续传、HLS
│   ├── db/            # Room 数据库、实体、DAO 与迁移
│   ├── update/        # GitHub Releases 更新检查
│   ├── prefs/         # SharedPreferences 设置项
│   ├── backup/        # 认证加密备份 / 恢复
│   └── security/      # 口令派生与加解密
└── ui/
    ├── screens/       # 解析 / 网盘 / 下载 / 设置 / 预览 / 文件管理等页面
    ├── resolve/       # 分享详情页
    ├── login/         # 各平台登录页（WebView / 表单）
    ├── viewmodel/     # 各页面 ViewModel
    ├── components/    # 通用组件（列表工具栏、更新弹窗等）
    └── theme/         # 颜色、排版、形状、主题控制
```

## 开源协议与致谢

本项目基于开源项目 **CYQawa/YunX** 二次开发（原项目著作权归其作者所有），遵循 **GNU AGPL-3.0** 协议发布。

- 你可以在 AGPL-3.0 的条款下自由使用、修改与再分发本项目
- 再分发（包括通过网络提供服务）时必须保留本声明、许可证与完整源码
- 完整许可证见 [LICENSE](LICENSE)

## 免责声明

本应用仅供个人学习与技术交流使用，请勿用于任何商业用途。下载内容版权归原作者所有，请于下载后 24 小时内删除。使用本应用产生的任何后果由使用者自行承担。
