# Alger轻听（Alger Music TV）

原生重写的轻量网易云音乐 App（电视版 + 手机版两个 flavor），借鉴
[AlgerMusicPlayer](https://github.com/algerkong/AlgerMusicPlayer) 的功能与视觉
（深色界面、封面取色、全屏沉浸歌词），全部代码为原生 Java，不依赖 WebView。

- **tv flavor**：为老电视（Android 5.1.1 / API 22 / armeabi-v7a）设计，
  包名 `com.wushisan.algertv`，minSdk 22 / targetSdk 28，
  Leanback 电视 UI（遥控器焦点导航、横屏），产物 `AlgerTV-<版本>.apk`
  （当前 1.0.1：修复 1.0 的启动闪退——AppCompatActivity 与 Theme.Leanback
  主题冲突，宿主 Activity 已改 FragmentActivity；主题按 flavor 隔离，
  tv 用 Leanback 主题、phone 用 AppCompat 主题 PhoneTheme）
- **phone flavor**：手机触控 UI（竖屏），包名 `com.wushisan.algertv.phone`，
  minSdk 22 / **targetSdk 34**，产物 `AlgerPhone-1.0.apk`；
  前台服务声明 mediaPlayback 类型，Android 13+ 通知权限在播放页按需请求
  （拒绝不阻断播放）；手机源集零 Leanback 依赖
- 技术：RecyclerView + Palette + MediaSessionCompat、Glide 4.16、OkHttp 3.12；
  数据层 / 队列 / LRC 解析为两 flavor 共用（`src/main`），UI 与播放 Service
  各 flavor 独立（`src/tv`、`src/phone`）
- 播放：系统 MediaPlayer + 前台 Service（队列、自动下一首、通知栏/媒体键控制）

## 数据源

- **主源（唯一内置）GDStudio**：`https://music-api.gdstudio.xyz/api.php`
  （`types=search|url|lyric|pic|playlist&source=netease`，五端点已实测全通）
  搜索 / 歌单详情 / 320k 播放地址 / 歌词 / 封面全走此源；`types=playlist`
  返回网易云完整歌单 JSON（含全部 tracks）。
- **首页**直接由 GDStudio 填充：排行榜行 = 热歌榜 3778678、飙升榜 19723756、
  新歌榜 3779629、原创榜 2884035、网易云中文说唱榜 991319590；推荐歌单行 =
  美国Billboard榜 60198、网易云欧美热歌榜 2809513713、网易云电音榜 1978921795、
  网易云韩语榜 745956260、UK排行榜周榜 180106、美国热门歌曲 11641012、
  网易云古典榜 71384707、日本Oricon榜 60131（均为网易云官方长青榜单，
  2026-10-22 实测存活、播放量均在千万级以上；名称/封面/播放量每次从
  服务器刷新，写死 ID 与名称仅作离线兜底）。
- **原 A 源 mc.alger.fun 已弃用**：其公共 API 服务器已被网易封禁，作者已宣布
  移动端停止维护（AlgerMusicPlayer issue #698/#748/#764），国内国外均不可用，
  代码不再默认调用它。
- **自定义 API 地址（可选，默认关）**：首页「设置」行可填入自建的
  NeteaseCloudMusicApi 地址；填入后搜索/歌单/播放/歌词优先走该地址，
  失败自动回落 GDStudio。首页固定走 GDStudio 精选。

## 构建

本机不构建，全部走 GitHub Actions（`.github/workflows/build.yml`）：

- push 到 `main` 或 tag `v*` 触发：还原签名（Secrets）→ `assembleRelease`
  （同时产出 tv 与 phone 两个 release APK）→ 产物以 base64 分片提交到
  `build-output` 分支（电视版在 `apk/`、手机版在 `phone-apk/`，
  含 `build-info.txt` 与构建日志）
- 仓库内二进制图片素材以 base64 形式存放于 `assets-src/`，CI 构建前解码回 `res/`

## 签名（固定）

release 签名使用固定 keystore，凭据只存 GitHub Actions Secrets：
`KEYSTORE_BASE64` / `KS_PASS` / `KEY_ALIAS` / `KEY_PASS`。
Secrets 未配置时 CI 产出未签名 APK（交付时由维护者在本地用同一 keystore 签名，
保证后续升级可直接覆盖安装）。

## 冒烟验证

`build.yml` 内置 smoke job（ARM runner + redroid 11）：构建完成后由工作流
自己下载刚构建的两个 APK，逐个 adb install + 启动主 Activity，
检查进程存活与 logcat 有无 FATAL EXCEPTION，把 `report.txt`、logcat 与截图
用工作流 token 提交回 `build-output/smoke-results/`。
（旧的 `.github/workflows/smoke.yml` 分片推送方案已废弃——逐片推送会产生
大量用户审核卡；smoke.yml 文件可后续删除，不再触发任何发布动作。）

## v1 范围

免登录：首页推荐歌单/排行榜、歌单详情、搜索、全屏歌词播放页、底部状态在首页有
「正在播放」入口。不含登录、下载、EQ、MV、本地音乐（登录计划 v1.1 扫码登录）。
