# ExoPlayer 播放器 Demo 产品需求文档（PRD）

| 文档版本 | 发布日期 | 维护人 | 代码基线 | 读者 |
|---|---|---|---|---|
| v1.4 | 2026-11-07 | NuovoVita | `main`（M4 路线调研先行 + M3 体验增强） | 开发团队 · 技术评审 |

> 基于 ExoPlayer 的 Android 原生视频播放验证项目 —— 从在线播放、播放控制，走向跨应用协作与企业视频源集成。

## 目录

- [1. 产品概述](#1-产品概述)
- [2. 目标与非目标](#2-目标与非目标)
- [3. 用户与场景](#3-用户与场景)
- [4. 功能需求](#4-功能需求)
- [5. 非功能需求](#5-非功能需求)
- [6. 技术方案概要](#6-技术方案概要)
- [7. 里程碑规划](#7-里程碑规划)
- [8. 风险与依赖](#8-风险与依赖)
- [9. 附录](#9-附录)

## 1. 产品概述

### 1.1 背景与问题

团队需要一条在 Android 原生应用中嵌入视频播放的技术路径，并评估其对接企业内容平台（SharePoint、Azure Media Services）的可行性。商业播放器 SDK 黑盒程度高、定制成本大，而开源方案 ExoPlayer 需要用真实场景验证：起播表现、倍速控制、轨道切换，以及跨应用复用播放内核的工程形态。exojavademo 即为该验证的载体项目。

### 1.2 产品定位

面向开发团队的**技术验证 Demo**，不面向终端用户发布。产出物有两类：可运行的验证 App（Java，3 个界面 + 1 个 AIDL 服务），以及随仓库维护的知识库（README 中的流媒体协议选型、Azure + SharePoint 方案调研）。项目以最小功能集跑通「在线播放 → 播放控制 → 跨应用协作 → 企业源集成」的关键路径。

### 1.3 现状快照

| 项 | 现状 |
|---|---|
| 应用标识 | `com.example.exo_java_demo`，versionName 1.0（versionCode 1） |
| 系统范围 | minSdk 24（Android 7.0）～ targetSdk 34（Android 14）/ compileSdk 36 |
| 技术栈 | Java 8 · androidx.media3 `1.11.1`（media3-exoplayer + media3-ui）· AppCompat / Material / ConstraintLayout |
| 已交付功能 | HTTPS 渐进式在线播放、倍速选择（0.5×–2.0×）、轨道选择（画质/音轨 + 自动档）、播放源管理（预置源/历史/URL 输入）、MediaPlayer+SurfaceView 备用引擎、AIDL 客户端/服务端（自托管）、SharePoint 认证实验页 |
| 进行中 | M2 代码完成；真机验收（T1.1/T1.2/T1.4）待设备可用 |
| 默认测试源 | 预置多源列表首条（W3C Sintel MP4）；原 gtv-videos-bucket 整桶 403，2026-11 更换 |
| 持续集成 | GitHub Actions（`android.yml`） |

## 2. 目标与非目标

### 2.1 项目目标

- 跑通 HTTPS 渐进式视频播放，验证起播速度、稳定性与生命周期行为
- 验证核心播放控制：倍速（已交付）、轨道选择（待恢复）
- 验证 AIDL 跨进程服务通信（自托管绑定，可选跨应用），为「播放内核与宿主 App 分离」探路
- 沉淀流媒体协议与企业视频方案知识库，支撑后续协议选型（HLS / DASH）
- 全程保持 CI 可构建、仓库可复现

### 2.2 非目标（本期不做）

- 不做内容管理、账户体系与推荐分发
- 不做 DRM 内容保护验证（远期评估）
- 不做 iOS / 跨端方案
- 不上架应用商店，不承诺向后兼容的对外接口
- 不做直播（低延迟推流）场景验证

## 3. 用户与场景

### 场景 A · 播放验证（开发自用）

开发者替换播放源地址后启动 App，观察起播耗时、各倍速档位下的音画表现与横竖屏切换行为，判断一段视频源在目标设备上是否可用。

### 场景 B · 跨应用协作（架构验证）

本应用既是 AIDL 服务端（内置 `MyService`）也是客户端：主界面绑定自托管服务并调用 `add(5, 6)`，结果直接显示在界面上，无需安装外部应用即可验证跨进程通信链路；需要验证跨应用绑定时，将绑定目标切换回外部应用 `com.example.mediaplayerwithsurfaceview`（manifest 的 `<queries>` 已声明其可见性）。

### 场景 C · 企业源评估（方案预研）

技术评审者依据本 Demo 的播放表现与 README 知识库，评估直接播放 SharePoint 上的静态视频文件（HTTP/HTTPS）与经 Azure Media Services 转为 HLS/DASH 自适应流两条路线的成本与体验差异。

## 4. 功能需求

共 10 项需求。优先级定义：**P0** 验证项目成立的前提；**P1** 本迭代主线；**P2** 后续迭代。

### 需求总览

| ID | 需求 | 优先级 | 状态 | 里程碑 |
|---|---|---|---|---|
| FR-1 | 在线视频播放 | P0 | 已交付 | M0 |
| FR-2 | 倍速播放 | P0 | 已交付 | M0 |
| FR-3 | 轨道选择（恢复） | P1 | 已交付（待真机验收） | M2 |
| FR-4 | 跨应用服务通信（AIDL） | P1 | 已交付（待真机验收） | M1 |
| FR-5 | 播放源管理 | P1 | 已交付（待真机验收） | M2 |
| FR-6 | 自适应流协议验证 | P2 | 规划中 | M3 |
| FR-7 | 企业视频源集成 | P2 | 路线已定（T4.1） | M4 |
| FR-8 | 播放体验增强 | P2 | 已交付（待真机验收） | M3 |
| FR-9 | 备用播放引擎（MediaPlayer+SurfaceView） | P2 | 已交付 | M1 |
| FR-10 | SharePoint 认证实验页 | P2 | 已交付（实验） | M1（预研 M4） |

### FR-1 在线视频播放

**状态：已交付 · 优先级：P0 · 里程碑：M0**

通过 androidx.media3 `ExoPlayer` + `PlayerView` 播放 HTTPS 渐进式视频流（默认源为预置列表首条，见 FR-5；原默认 TearsOfSteel 所在 gtv-videos-bucket 已整桶 403，2026-11 更换），启动即自动播放（`playWhenReady`），播放期间保持屏幕常亮（`keepScreenOn`）；横竖屏切换由 `configChanges` 承接，不重建 Activity。

验收标准：

- [ ] 联网状态下冷启动进入主界面后 3 秒内开始出画面（正常网络）
- [ ] 横竖屏旋转播放不中断、不重启从头播放
- [ ] 退出界面（`onDestroy`）后播放器实例正确释放，无泄漏

### FR-2 倍速播放

**状态：已交付 · 优先级：P0 · 里程碑：M0**

主界面提供入口，弹出倍速选择对话框（RecyclerView 列表）：0.5× / 1.0× / 1.5× / 2.0× 四档，经 `PlaybackParameters` 即时生效；当前档位以对勾标记，选择后对话框关闭。

验收标准：

- [ ] 任一档位点击后 200ms 内可感知变速，音调保持正常（TimeStretch）
- [ ] 当前档位对勾标记与实际生效速度一致；重新打开对话框状态正确
- [ ] 档位切换不引起播放中断或重新缓冲超过 1 秒

### FR-3 轨道选择（恢复）

**状态：已交付（待真机验收） · 优先级：P1 · 里程碑：M2**

主界面提供「画质 / 音轨」入口，弹出选择对话框：基于 Media3 `TrackSelectionParameters` / `TrackSelectionOverride` 实现强制指定与「自动」档（自动 = `clearOverridesOfType`），仅列出设备支持的轨道，当前生效档位以对勾标记。该功能曾于 2024-03 交付（commit `e837934`），后在倍速功能重构（commit `4bd82aa`）中随旧实现一并移除，本次在新结构上恢复。

验收标准：

- [ ] 对多码率测试源可列出全部可选画质/音轨，含「自动」
- [ ] 手动指定后播放器切换至对应轨道；选「自动」恢复自适应逻辑
- [ ] 与倍速功能共存，互不影响状态

> 恢复实现时应参照官方 Track Selection 文档（见附录），避免回到旧的全量遍历写法。

### FR-4 跨应用服务通信（AIDL）

**状态：已交付（自托管服务端，待真机验收） · 优先级：P1 · 里程碑：M1**

通过 AIDL 接口 `IMyAidlInterface`（演示方法 `add(int, long)`）完成跨进程调用。服务端 `MyService`（集成自 MediaPlayerWithSurfaceView 工程）已内置进本应用，客户端默认绑定自托管服务并在主界面显示调用结果，无需安装外部应用即可验证；跨应用绑定为可选项（切换绑定目标为外部应用，manifest `<queries>` 已声明其可见性）。Gradle 已启用 `buildFeatures.aidl`。

验收标准：

- [ ] 绑定自托管服务成功，主界面显示 `add(5, 6) = 11`
- [ ] 服务不可达或绑定失败时，App 不崩溃，界面给出可读提示
- [ ] 远端异常（RemoteException）以界面提示呈现，不以上层崩溃（RuntimeException）冒泡
- [ ] 生命周期正确：界面销毁时 `unbindService`，重复绑定不泄漏连接

> 已按 v1.0 验收要求修正：绑定失败与 RemoteException 均走界面提示，调用结果经界面展示而非 `System.out`。

### FR-5 播放源管理

**状态：已交付（待真机验收） · 优先级：P1 · 里程碑：M2**

主界面提供「播放源」入口，弹出管理对话框：URL 地址输入即播、预置示例源列表（渐进式 MP4 / HLS 多码率 / DASH 多码率各一条，2026-11 逐一验证可达）、最近播放历史（SharedPreferences 持久化，去重、新在前、上限 10 条、可一键清空）。播放错误经 `onPlayerError` 以 Toast 呈现错误码。

验收标准：

- [ ] 输入合法 HTTP(S) 地址即可播放，无需改代码重新编译
- [ ] 历史记录在杀进程后保留；可一键清空
- [ ] 非法地址或不可达源在 5 秒内给出明确错误提示（不无限转圈）
  > 实现注：错误提示依赖 ExoPlayer 默认加载重试耗尽（约 3 次重试），典型不可达源在数秒内触发；5 秒硬性时限待真机实测确认。

### FR-6 自适应流协议验证

**状态：规划中 · 优先级：P2 · 里程碑：M3**

播放 HLS（m3u8）与 DASH（mpd）测试源，验证 ABR 自适应行为：观察网络受限时的码率升降、卡顿与恢复，为后续企业源协议选型提供实测依据。

验收标准：

- [ ] 至少各一条 HLS、DASH 公开测试源可正常起播与切换画质
- [ ] 模拟弱网（限速工具）下可观察到码率自适应下调与恢复
- [ ] 实测结论回写 README 知识库

### FR-7 企业视频源集成

**状态：路线已定（T4.1 调研完成）· 优先级：P2 · 里程碑：M4**

打通 SharePoint 托管内容的鉴权播放 PoC：携带凭据请求受保护资源，处理 401/403 与令牌刷新。~~原「SharePoint 静态文件 vs Azure Media Services 转 HLS/DASH」双路线~~ 已随 **AMS 于 2024-06-30 退役**收敛为单路线（决策记录：`docs/enterprise-source-research.md`）：

- **主路线 A**：FR-10 WebView 登录取得 rtFa / FedAuth → 注入 Media3 `CookieHandler` → ExoPlayer 播放 SharePoint 文件直连 URL（渐进式，权限复用 SharePoint 站点/库权限）；
- **备选 A2**：MSAL Bearer 调 Graph `/drive/items/{id}/content` 换预认证短时 URL（免 WebView，需注册 Entra 应用）；
- **演进 B（暂缓）**：Blob 离线打包 HLS/DASH + Azure CDN，承接弱网 ABR 需求（AMS 官方迁移工具即此形态）；Microsoft Stream 不开放第三方原生播放，排除。

验收标准：

- [ ] 带权限的账号可播放受保护视频；无权限账号得到明确拒绝提示
- [ ] 凭据不落明文日志，不硬编码进仓库
- [ ] 输出集成成本与限制清单（权限模型、格式、缓存策略、Cookie 生命周期）

### FR-8 播放体验增强

**状态：已交付（待真机验收） · 优先级：P2 · 里程碑：M3**

补齐 Demo 级播放体验：缓冲态由 PlayerView 内置 `show_buffering` 呈现；播放错误升级为居中错误面板（错误码 + 一键重试，重试对当前源重新 prepare）；「后退/前进 10s」按钮（`seekBack`/`seekForward` + 10s 步长）；播放区手势 —— 单击切换控制条、横滑 seek（松手生效，时长等比映射、居中提示目标位置）、左半屏竖滑调亮度/右半屏调音量（滑动即生效、百分比提示）；屏幕左右边缘 24dp 不接管，避让系统返回手势。

验收标准：

- [ ] 缓冲与错误状态均有可见 UI，错误可一键重试
- [ ] 手势操作不与系统返回/通知栏手势冲突

### FR-9 备用播放引擎（MediaPlayer + SurfaceView）

**状态：已交付 · 优先级：P2 · 里程碑：M1**

集成自 MediaPlayerWithSurfaceView 工程。`MediaPlayerActivity` 使用系统 `android.media.MediaPlayer` + `SurfaceView` 播放与主界面 ExoPlayer 相同的视频源，横屏展示，用于两种播放内核的直观对比（起播速度、画面、生命周期行为）。相对原工程修正了主线程同步 `prepare()` 的 ANR 隐患（改为先注册监听再 `prepareAsync()`），并补齐 surface 销毁时的资源释放。

验收标准：

- [ ] 与主界面播放同一视频源，横屏正常出画面出声
- [ ] 进入/退出页面、旋转屏幕无崩溃与泄漏（SurfaceView 销毁时释放 MediaPlayer）
- [ ] 页面切后台暂停播放，回到前台恢复

### FR-10 SharePoint 认证实验页

**状态：已交付（实验） · 优先级：P2 · 里程碑：M1（支撑 FR-7 / M4 预研）**

集成自 MediaPlayerWithSurfaceView 工程中被注释的 WebView Cookie 实验。`WebViewActivity` 内嵌 WebView 打开企业站点，页面加载完成后检查 Cookie 中是否出现 `rtFa` / `FedAuth`（SharePoint 认证 Cookie），只报告存在性、不展示 Cookie 内容，为 FR-7 携带凭据播放企业视频验证前置条件。

验收标准：

- [ ] 打开任意 HTTPS 站点可正常加载，返回键先回退网页历史
- [ ] 登录 SharePoint 后状态行正确显示 rtFa / FedAuth 检测结果
- [ ] Cookie 内容不出现在界面与日志中

## 5. 非功能需求

| 维度 | 要求 |
|---|---|
| 兼容性 | Android 7.0（API 24）至 Android 14（API 34）真机各至少一台通过主流程；arm64-v8a / armeabi-v7a / x86_64 可运行 |
| 性能 | 良好网络下起播 ≤ 3s；倍速切换感知延迟 ≤ 200ms；播放 30 分钟内存无持续增长 |
| 稳定性 | 主流程 ANR 率与崩溃率为 0；播放器实例随生命周期创建/释放；外部服务不可达不崩溃 |
| 安全 | 仅申请 `INTERNET`、`ACCESS_NETWORK_STATE` 两项权限；默认源走 HTTPS；无明文凭据入仓 |
| 可维护性 | Java 8 语法约束；每次提交保持 GitHub Actions 构建绿灯；关键验证结论回写 README |

## 6. 技术方案概要

当前为单模块应用：`MainActivity`（ExoPlayer 播放、倍速对话框、播放源与轨道选择对话框、AIDL 客户端与功能入口）+ `SpeedAdapter`（倍速列表 RecyclerView）+ `SourceRepository` / `SourceAdapter`（FR-5 播放源数据与列表）+ `TrackAdapter`（FR-3 轨道选择列表）+ `MyService`（AIDL 服务端，自托管）+ `MediaPlayerActivity`（MediaPlayer+SurfaceView 备用引擎）+ `WebViewActivity`（SharePoint 认证实验页）+ AIDL 接口定义（`com.example.service.IMyAidlInterface`）。UI 层 AppCompat / Material / ConstraintLayout，播放层 androidx.media3 `1.11.1`（media3-exoplayer + media3-ui，compileSdk 36 / AGP 8.10.1 / Gradle 8.11.1）。

> **迁移已完成（M2，commit `908f427`）。** ExoPlayer 2.19.1 → androidx.media3 1.11.1 的包名重命名与构建链升级已落地，`assembleDebug` 本地与 CI 均绿灯；评估与实施过程见 `docs/media3-migration.md`。行为回归（起播、倍速、切换源）并入 T1.1 真机验收。

### 已知技术债

- ~~播放地址硬编码在 `MainActivity` 与 `MediaPlayerActivity`~~ 已随 FR-5 解决（`SourceRepository` 预置源 + 历史，`MediaPlayerActivity` 复用首条预置源）
- ~~轨道选择功能待在新结构上恢复（FR-3）~~ 已恢复（M2）
- ~~`MediaPlayerActivity` 错误处理较简陋（`printStackTrace`）~~ 已补齐错误回调与用户提示（M2 技术债清理）；`WebViewActivity` 无 catch 块、无此问题。弱网与坏地址场景仍需真机过一遍（并入 T1.1）
- ~~历史实验代码（带宽设置，commit `5cdb4bf`）已清理，但结论未沉淀进文档~~ 已如实沉淀（README「历史实验记录」：当时未记录量化数据；量化结论推迟至 T3.1 弱网 ABR 实测）
- 无自动化 UI 测试，仅存模板单测/仪器测试（最小测试集仍规划于 M3）

## 7. 里程碑规划

| 阶段 | 主题 | 内容 | 状态 | 时间 |
|---|---|---|---|---|
| M0 | 基础播放与控制 | 在线播放、倍速选择、轨道选择（初版，后移除）、CI 搭建、README 知识库 | 已完成 | 2024-03 ～ 2024-04 |
| M1 | 跨应用通信与双工程集成 | FR-4 AIDL 自托管绑定与调用（含异常与生命周期收敛）、FR-9 MediaPlayer 备用引擎、FR-10 SharePoint 认证实验页 | 代码完成并提交，待真机验收（T1.1/T1.2/T1.4） | 当前迭代 |
| M2 | 播放能力回归 | FR-3 轨道选择恢复、FR-5 播放源管理、Media3 迁移（评估 + 实施）、技术债清理 | 代码完成，待真机验收（并入 T1.1） | 当前迭代 |
| M3 | 自适应流验证 | FR-6 HLS/DASH/ABR 实测、FR-8 播放体验增强 | T3.2 代码完成（FR-8 待真机验收）；FR-6 实测与 T3.3 测试集需真机/模拟器 | 当前迭代 |
| M4 | 企业源 PoC | FR-7 SharePoint 鉴权播放验证（路线已定：Cookie 注入直连，AMS 已退役出局，见 `docs/enterprise-source-research.md`） | 调研完成，PoC 待排期 | 待排期 |

## 8. 风险与依赖

| 风险 / 依赖 | 等级 | 影响 | 应对 |
|---|---|---|---|
| ~~ExoPlayer 2.x 已 EOL，无安全修复~~ | ~~高~~ 已消除 | 新 API 不可用；已知缺陷不再修复 | M2 已完成 Media3 1.11.1 迁移（commit `908f427`） |
| FR-4 曾依赖目标应用 `mediaplayerwithsurfaceview` 同设备安装 | 低（已缓解） | 缺少宿主时无法验证绑定链路 | 服务端已内置为 `MyService`（自托管），可独立验证；跨应用绑定保留为可选项 |
| 企业源鉴权模型不确定（SharePoint 权限 / Azure 令牌） | 低（已缓解） | M4 范围可能扩大或受阻 | T4.1 已核实：权限复用 SharePoint 站点/库权限，FedAuth ~10h（条件访问可至 ~1h）、rtFa 静默续期；AMS 路线随其 2024-06-30 退役出局，主路线收敛为 SharePoint Cookie 注入（`docs/enterprise-source-research.md`）。剩余开放项：租户条件访问对 WebView 登录的影响、非交互拉流限流阈值，T4.2/T4.3 实测 |
| 默认测试源为 Google 公网存储，部分网络不可达 | 低（已缓解） | 演示与验证受阻 | FR-5 已落地预置多源列表（MP4/HLS/DASH 各一条，2026-11 验证可达）；原 gtv 桶整桶 403 已弃用 |
| 无自动化 UI 测试，回归靠手测 | 低 | 重构风险放大 | Media3 迁移已靠 CI 构建绿灯 + 代码评审承接；最小仪器测试集仍规划于 M3 |

## 9. 附录

### 术语表

| 术语 | 释义 |
|---|---|
| 渐进式流 | 通过 HTTP(S) 整体下载单个媒体文件的播放方式，无自适应码率 |
| ABR | 自适应比特率：播放器按网络状况在多档码率间实时切换 |
| HLS / DASH | 基于 HTTP 分段传输的主流自适应流协议（.m3u8 / .mpd 清单） |
| AIDL | Android Interface Definition Language，跨进程服务接口定义与调用机制 |
| Media3 | Android 官方媒体库族，ExoPlayer 的后继形态（androidx.media3） |

### 参考资料

- [ExoPlayer / Media3 官方文档](https://developer.android.com/media/media3/exoplayer?hl=zh-cn)
- [轨道选择（Track Selection）](https://developer.android.com/media/media3/exoplayer/track-selection?hl=zh-cn#java)
- [官方演示应用](https://developer.android.com/media/media3/exoplayer/demo-application?hl=zh-cn)
- [公开测试视频源列表](https://gist.github.com/jsturgis/3b19447b304616f18657)
- 仓库 README：流媒体协议对比、Azure Media Services + SharePoint 方案调研

### 文档版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-11-07 | 首次成文：依据 main @ 4bd82aa 与工作区未提交改动整理现状，定义 FR-1～FR-8 与 M1～M4 规划 |
| v1.1 | 2026-11-07 | 集成 ExoPlayerCustomUI（功能本库已具备，无新增）与 MediaPlayerWithSurfaceView（AIDL 服务端自托管、MediaPlayer+SurfaceView 引擎、SharePoint Cookie 实验）；FR-4 状态更新为已交付，新增 FR-9/FR-10，`assembleDebug` 构建通过；同日完成全文一致性校订（版本头、总览表状态、产品定位、FR-5 范围、FR-10 里程碑归属） |
| v1.2 | 2026-11-07 | M2 落地：ExoPlayer 2.19.1 → androidx.media3 1.11.1 迁移完成（构建链升级 compileSdk 36 / AGP 8.10.1 / Gradle 8.11.1）；FR-3 轨道选择、FR-5 播放源管理交付（待真机验收，并入 T1.1）；技术债清理（MediaPlayerActivity 错误处理、带宽实验结论如实沉淀）；默认源因 gtv 桶整桶 403 更换为多协议预置源（2026-11 验证可达） |
| v1.3 | 2026-11-07 | T4.1 企业源路线调研完成（提前执行，先于 M3）：AMS/Azure Media Player 均已 2024-06-30 退役且无第一方替代，FR-7 路线收敛为「SharePoint Cookie 注入直连」（备选 Graph 预认证 URL；ABR 演进挂 Blob+CDN 离线打包；Microsoft Stream 排除）；核实 rtFa/FedAuth 生命周期与 SharePoint 权限模型；决策记录 `docs/enterprise-source-research.md` |
| v1.4 | 2026-11-07 | T3.2 FR-8 播放体验增强代码交付（待真机验收）：PlayerView 内置缓冲指示、错误面板 + 一键重试（替代纯 Toast）、±10s 快进快退、横滑 seek / 左右半屏竖滑亮度音量手势（边缘 24dp 避让系统手势）；lint 与双构建绿灯 |
