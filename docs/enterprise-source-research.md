# 企业视频源路线调研（T4.1）

> 任务：TASKS.md M4 / T4.1 · 关联：PRD FR-7（企业视频源集成）、FR-10（SharePoint 认证实验页）
> 日期：2026-11-07 · 结论将同步回写 PRD 与 README

## 0. 结论速览

| 路线 | 定位 | 判定 |
|---|---|---|
| **A. SharePoint 静态文件 + Cookie 注入** | **M4 PoC 主路线** | ✅ 采用：零新增服务，FR-10 前置已就位，直接验证 FR-7 鉴权环节 |
| A2. Microsoft Graph API 临时下载 URL | 备选鉴权通道 | 备用：MSAL Bearer 换 302 预认证短时 URL，免 WebView；引入 Graph 权限申请 |
| B. Azure Blob + 离线 HLS/DASH 打包 + CDN | ABR 演进路线 | 暂缓：需要多码率/弱网自适应时再上（对应 M3/T3.1 的诉求） |
| C. 第三方视频托管（Bitmovin / Mux / Bunny 等） | 商用备选 | 不采用：Demo 阶段引入外部供应商成本与采购流程，仅记录 |
| D. Microsoft Stream（M365） | — | ❌ 排除：不向第三方原生播放器开放清单/播放端点，仅支持 iframe 嵌入 |

**一句话**：FR-7 的验证对象从「SharePoint / Azure Media Services」收敛为「**SharePoint（Cookie 鉴权直连）**」；AMS 路线随服务退役作废，ABR 需求由路线 B 承接（延后）。

## 1. 背景与问题

- PRD v1.0–v1.2 与 README 的企业源方案写在「SharePoint 静态文件 vs Azure Media Services 转 HLS/DASH」两条路线上；
- **Azure Media Services（AMS）已于 2024-06-30 退役**，Azure Media Player 同日退役，新建账户被全面禁止 —— README 中「Azure Media Services + SharePoint 配合」一节的前提已不存在；
- 需确定替代路线并确认 SharePoint 权限模型与令牌（rtFa / FedAuth）生命周期，供 T4.2–T4.4 执行。

## 2. 官方事实核对（2026-11 查证）

### 2.1 Azure Media Services 退役（已核实）

Microsoft Learn《Azure Media Services retirement guide》（2024-06-18 发布，官方存档）：

- 2023-06 宣布退役，**2024-06-30 停止服务**：所有 AMS 账户停止流式传输，转为只读约 90 天后自动删除；v2/v3 API 同日同时退役；**新建 AMS 账户在所有区域被阻止**；
- **Azure Media Player 同日（2024-06-30）退役**；
- **无第一方替代服务**。官方迁移去向：合作伙伴（Bitmovin、MediaKind、Ravnur）、系统集成商（Eyevinn、Southworks、WhDiYo），或官方开源迁移工具 `Azure/azure-media-migration` 把存量资产转成「**存储直出 HLS/DASH + 自配 CDN**」（即本调研的路线 B 形态，微软自己背书其可行）；
- 音视频分析能力迁往 Azure Video Indexer（未退役）。

### 2.2 SharePoint 权限模型与 Cookie 生命周期（已核实）

- **鉴权链**：用户经 Entra ID（原 AAD）认证 → SharePoint STS 发 SAML 令牌 → 换取 **`FedAuth` 会话 Cookie**；伴随的 **`rtFa` 是刷新 Cookie**，用于在 FedAuth 临近过期时**静默续期**，用户登出时删除（Microsoft Learn《SharePoint authentication》）。
- **生命周期**：FedAuth 默认有效期约 **10 小时**（SharePoint STS 默认，社区多方交叉印证；本地版可通过 `SPSecurityTokenServiceConfig` 配置）；启用**条件访问会话控制**后可被压缩到约 **1 小时**，SharePoint Online 会在幕后轮换/续期 Cookie。
- **权限模型**：视频文件就是普通 SharePoint 列表项，权限 = 站点/库/项级权限（用户/组/分享链接），无视频专用 ACL —— 意味着「带权限账号可播、无权限账号 401/403」可以直接复用 SharePoint 权限体系验证，无需额外建模。
- **对播放器的要求**：请求需携带 Cookie（而非 Authorization 头）访问 `*.sharepoint.com` 文件直连 URL；Media3 `DefaultHttpDataSource` 原生走 JVM `CookieHandler`，**在构建播放器前 `CookieHandler.setDefault(cookieManager)` 并把 WebView 拿到的 rtFa/FedAuth 塞进去**，即可让清单、分段、Range 请求全程带 Cookie，且天然跟随 302（静态 `setDefaultRequestProperties` 塞 Cookie 头做不到这点，社区与官方 issue #7795 均推荐 CookieManager 方案）。
- **文件限制**：SharePoint Online 单文件上限 **250 GB**（官方服务描述），远超 Demo 需求；未文档化「非交互式客户端连续 Range 拉流」的限流阈值 —— 列为风险（见 §5）。

### 2.3 Microsoft Stream 不开放第三方原生播放（已核实）

未找到任何官方 API 向外部播放器提供 Stream 视频的 HLS/DASH 清单；Stream（on SharePoint）播放端点是鉴权且短时效的内部地址，官方支持的对外方式只有 **iframe 嵌入**。对 ExoPlayer 原生播放而言不可用 → 排除（D）。

### 2.4 与本工程现状的衔接

- FR-10（`WebViewActivity`）已能：WebView 打开企业站点 → 登录 → 检测 rtFa/FedAuth 存在性（不展示内容）；
- 缺的只是：把这两个 Cookie 从 `CookieManager.getInstance()` 取出 → 注入 Media3 的 `CookieHandler` → 用文件直连 URL 播放。这正是 T4.2 的最小闭环。

## 3. 候选路线详情

### A. SharePoint 静态文件 + Cookie 注入（主路线，M4 PoC）

- **链路**：`WebViewActivity` 登录 → 取 rtFa/FedAuth → `cookieManager.put(uri, headers)` 注入 → `ExoPlayer` 播放 `https://tenant.sharepoint.com/.../video.mp4` 直连 URL（渐进式 + Range）。
- **优点**：零新增云服务与费用；权限模型复用 SharePoint；FR-10/FR-1 现成；验证的正是 FR-7 的鉴权环节本体。
- **局限**：**无 ABR**（静态 MP4 单码率）；Cookie 有生命周期（§2.2），过期需重新走 WebView；企业租户若开条件访问/Intune 策略，WebView 内登录也可能受 MFA/设备合规约束（以实测为准）。

### A2. Graph API 临时下载 URL（备选鉴权通道）

- **链路**：MSAL 设备码/交互式登录拿 Bearer → `GET /drive/items/{id}/content`（带 `Authorization` 头）→ 302 到**预认证短时下载 URL** → 交给 ExoPlayer 直接播。
- **优点**：不依赖 WebView 抓 Cookie；URL 短时效天然满足「凭据不落明文」。
- **代价**：注册 Entra 应用 + `Files.Read`/`Sites.Selected` 等权限申请；多一套 MSAL 依赖。作为 A 受阻时的备选。

### B. Azure Blob + 离线打包 HLS/DASH + Azure CDN（ABR 演进路线，暂缓）

- **链路**：上传时（或发布流水线）用 ffmpeg/Shaka Packager 离线打包多码率 HLS/DASH → Blob 静态托管 → Azure CDN 前置；鉴权用 SAS 或 CDN 令牌。
- **依据**：AMS 官方迁移工具对存量资产采用的就是「存储直出 HLS/DASH + CDN」形态（§2.1），说明该路线微软自认可行，只是没有托管控制面。
- **定位**：当 M3/T3.1 验证出弱网 ABR 是硬需求、且企业源必须提供多码率时启用；Demo 阶段不投入。

### C. 第三方托管/转码（商用备选，不采用）

Bitmovin（微软官方点名的 AMS 迁移伙伴）、MediaKind、Ravnur，或 Mux/Bunny Stream/Cloudflare Stream 等现代托管。提供 HLS/DASH + 令牌鉴权 + API，但引入供应商与采购流程，超出 Demo 范围；若未来转产品化再评估。

## 4. 对 M4 任务的影响（范围修订建议，已同步 TASKS.md）

- **T4.2 凭据播放链路**：按路线 A 执行 —— WebView 登录 → rtFa/FedAuth 注入 Media3 `CookieHandler` → 播放 SharePoint 直连 URL；A2 作为备选记录。
- **T4.3 鉴权失败路径**：401/403 的主要成因为 FedAuth 过期（默认 ~10h、条件访问可至 ~1h）或账号无站点权限 → 提示并引导回 WebView 重新登录刷新 Cookie；Cookie 全程不落日志（FR-7 红线）。
- **T4.4 成本与限制清单**：按路线 A 事实填写（权限=SharePoint 原生、格式=渐进式 MP4 无 ABR、Cookie 生命周期、250GB 上限、限流未文档化）。
- **PRD FR-7**：验收对象从「SharePoint / AMS」改为「SharePoint（Cookie 鉴权）」，ABR 演进挂到路线 B。

## 5. 开放问题与风险

| 项 | 状态 | 处置 |
|---|---|---|
| 非交互拉流的限流阈值未文档化 | 开放 | T4.2 真机实测长视频（>30min）观察是否被 429/中断 |
| 企业租户条件访问 / MFA / Intune 对 WebView 登录的影响 | 待实测 | T4.2 用真实租户验证；受阻则评估 A2（Graph） |
| FedAuth 实际有效期因租户策略而异 | 待实测 | T4.3 记录实测值，写进 T4.4 清单 |
| 直连 URL 形态（`/:u:/` 分享链 vs 库内路径）与 Range 兼容性 | 待实测 | T4.2 验证两种 URL 形态 |

## 6. 参考来源（2026-11-07 查证）

- [Azure Media Services retirement guide — Microsoft Learn](https://learn.microsoft.com/en-us/azure/media-services/latest/azure-media-services-retirement)（退役时间线、伙伴迁移、静态迁移工具、AMP 同日退役）
- [Azure/azure-media-migration — GitHub](https://github.com/Azure/azure-media-migration)（存量资产转存储直出 HLS/DASH + CDN）
- [SharePoint authentication — Microsoft Learn](https://learn.microsoft.com/en-us/sharepoint/authentication)（FedAuth 会话 Cookie、rtFA 刷新 Cookie、登出删除）
- [SharePoint Online limits — Microsoft Learn](https://learn.microsoft.com/en-us/office365/servicedescriptions/sharepoint-online-service-description/sharepoint-online-limits)（250GB 单文件等）
- FedAuth 默认 ~10h 与条件访问压缩会话：[SharePoint StackExchange #32676](https://sharepoint.stackexchange.com/questions/32676/fedauth-cookie-set-to-expire-only-10-hours-after-creation)、[Office365-REST-Python-Client #62](https://github.com/vgrem/Office365-REST-Python-Client/issues/62)
- Media3 Cookie 注入：[ExoPlayer issue #7795](https://github.com/google/ExoPlayer/issues/7795)、[StackOverflow #78529044](https://stackoverflow.com/questions/78529044/how-can-i-include-specific-cookies-in-a-media-request-with-media3-exoplayer)
