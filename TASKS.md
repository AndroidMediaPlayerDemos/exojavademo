# exojavademo 任务拆解

| 来源 | 日期 | 估时单位 | 维护人 |
|---|---|---|---|
| PRD v1.1 | 2026-11-07 | 人日（单人） | NuovoVita |

**约定**

- 每个任务的完成定义 = PRD 中对应 FR 的验收清单勾选完毕；本文任务勾选只表示「任务做完」，验收以 PRD 为准
- 所有提交保持 GitHub Actions 构建绿灯
- 上一里程碑到达完成门后，才启动下一里程碑的开发任务
- M1 收尾前不启动 M2 开发任务

---

## M1 跨应用通信与双工程集成（收尾）

> 代码已完成（工作区），本阶段只做验收与提交。关联：FR-4 / FR-9 / FR-10。

- [ ] **T1.1 真机验收回归**（0.5–1 人日）
  范围：FR-1/FR-2 基础回归；FR-4 主界面 AIDL 结果显示与失败路径；FR-9 横屏播放、旋转、后台恢复；FR-10 WebView 加载与 Cookie 检测。弱网（限速）与坏地址场景各过一遍。
  产出：PRD 验收清单勾选 + 遗留问题清单。

- [ ] **T1.2 验收问题修复**（估时视 T1.1 结果而定）
  已知薄弱点：`MediaPlayerActivity` / `WebViewActivity` 错误处理仅有 `printStackTrace`（见 PRD 技术债）。

- [x] **T1.3 分批提交与推送**（0.5 人日）
  建议按文件边界拆分：① AIDL 自托管（`MyService`、`aidl/`、manifest 服务与 `<queries>`、gradle `aidl`）② `MediaPlayerActivity` 及布局 ③ `WebViewActivity` 及布局 ④ 主界面入口改造（`activity_main.xml`、`strings.xml`）⑤ 文档（`PRD.md`、`TASKS.md`）。

- [ ] **T1.4 跨应用绑定验证**（0.5 人日，可选）
  安装 MediaPlayerWithSurfaceView 应用，按 `MainActivity` 注释切换绑定目标为外部包名，验证 `<queries>` 生效与跨应用调用。

**M1 完成门**：PRD 中 FR-4 / FR-9 / FR-10 验收清单全部勾选；main 分支 CI 绿灯。

---

## M2 播放能力回归

> 关联：FR-3 / FR-5 / 技术债。依赖顺序：T2.1 ✅ → T2.5（迁移）✅ → T2.3 ✅ → T2.2 ✅ → T2.4 ✅。

- [x] **T2.1 Media3 迁移评估（Spike）**（2 人日预算，实用 0.5）
  对照 ExoPlayer 2.19.1 与 `androidx.media3` 的类名映射（`SimpleExoPlayer → androidx.media3.exoplayer.ExoPlayer` 等）、Gradle 依赖替换、已知行为差异；回归范围与工作量清单。
  产出：迁移决策记录（迁 / 不迁 / 何时迁），入库 `docs/` 或 README 附录；**决定 T2.2–T3.x 在哪条基线上实施**。
  **结果（2026-11-07）：迁**。目标 `androidx.media3 1.11.1`，连带升级 compileSdk 35 / AGP 8.7 / Gradle 8.10，预估 1–1.5 人日。决策记录：`docs/media3-migration.md`。

- [x] **T2.5 执行 Media3 迁移**（1–1.5 人日，由 T2.1 决策新增）
  按 `docs/media3-migration.md` 第 6 节清单执行：① 构建链升级（Gradle 8.10.2 / AGP 8.7.3 / compileSdk 35，单独提交先行）② 依赖坐标替换 + 3 文件包名替换 ③ CI 绿灯 + 真机回归（真机部分并入 T1.1 一并做）。
  注：原计划「T3.3 测试集提前作为迁移回归基线」需模拟器/真机，当前暂不可用；本轮回归以 CI 构建绿灯兜底，行为回归并入 T1.1。
  **结果（2026-11-07）：完成**（commit `3399b5c` 构建链 + `908f427` 迁移本体，CI 绿灯）。落地时实测 media3-ui 1.11.1 AAR 要求 compileSdk 36，构建链最终为 Gradle 8.11.1 / AGP 8.10.1 / compileSdk 36（高于 spike 估计，见 `docs/media3-migration.md` 修正记录）。真机行为回归并入 T1.1。

- [x] **T2.3 FR-5 播放源管理**（2–3 人日）
  URL 输入、最近播放历史（SharedPreferences 足够）、预置示例源列表（渐进式 MP4 / HLS / DASH 各至少一条）、非法或不可达源 5 秒内报错。
  排在 T2.2 之前的原因：轨道选择需要多码率源，本任务的预置源列表直接提供。
  **结果（2026-11-07）：代码完成**（commit `10c50bd`）。预置源 2026-11 逐一验证可达（W3C Sintel MP4 / Apple BipBop HLS / Akamai BBB DASH）；历史去重、新在前、上限 10 条、可清空。错误反馈经 `onPlayerError` Toast 错误码——「5 秒内」硬性时限待真机实测（并入 T1.1），故验收清单暂不勾。

- [x] **T2.2 FR-3 轨道选择恢复**（2 人日）
  基于多码率源（T2.3 预置）实现画质/音轨选择与「自动」档（`TrackSelectionParameters`）；实现参照官方 Track Selection 文档，避免回到旧的全量遍历写法。
  **结果（2026-11-07）：代码完成**（commit `47ba45d`）。`TrackSelectionOverride` 固定轨道、`clearOverridesOfType` 回自动，仅列支持轨道、对勾标记当前档。多码率源实测（切换生效、与倍速共存）待真机（并入 T1.1），验收清单暂不勾。

- [x] **T2.4 技术债清理**（1 人日）
  `printStackTrace` 收敛为用户可见的错误提示；带宽实验（commit `5cdb4bf`）结论回写 README。
  **结果（2026-11-07）：完成**。`MediaPlayerActivity` 补 `setOnErrorListener`（Toast what/extra）与 IOException 提示，坏源改用预置首条（gtv 桶 403）；带宽实验如实沉淀 README「历史实验记录」——当时只留了测量钩子、**无量化数据**，量化结论推迟至 T3.1；PRD 同步 v1.2。

**M2 完成门**：代码全部落地（T2.1–T2.5 ✅，CI 绿灯）；FR-3 / FR-5 验收清单勾选与迁移行为回归**待真机**（用户指示暂缓，统一并入 T1.1 一并执行）。

---

## M3 自适应流验证与体验增强

> 关联：FR-6 / FR-8 / 风险表第 5 条。

- [ ] **T3.1 FR-6 HLS/DASH/ABR 实测**（2 人日）
  复用 T2.3 预置源；限速工具模拟弱网，观察码率下调与恢复；实测结论回写 README 知识库。

- [x] **T3.2 FR-8 播放体验增强**（3 人日）
  缓冲/错误态 UI 与一键重试、±10s 快进快退、横滑 seek、音量/亮度竖滑手势。
  **结果（2026-11-07）：代码完成**。缓冲用 PlayerView 内置 `show_buffering="when_playing"`；错误升级为居中面板（错误码 + 重试，重新 prepare 当前源，换源/重新起播自动收起）；±10s 按钮（`seekBack/seekForward` + 10s 步长）；手势：单击切控制条、横滑 seek（松手生效，时长等比映射 + 目标提示）、左半屏亮度/右半屏音量竖滑（即滑即调 + 百分比提示），屏幕左右边缘 24dp 避让系统返回手势。Media3 opt-in API（seek 步长、控制条操作）按方法级 `@OptIn` 标注；本地 lint + 双构建绿灯。FR-8 验收清单勾选与手感确认待真机（并入 T1.1）。

- [ ] **T3.3 最小仪器测试集**（2 人日）
  主流程 Espresso：起播、倍速切换、源切换。（原「迁移前先行」计划已取消：T2.1 已决策迁移且 T2.5 先行落地，测试集直接基于 Media3 编写。）

**M3 完成门**：FR-6 / FR-8 验收清单全勾；测试集在 CI 中运行。

---

## M4 企业源 PoC

> 关联：FR-7（FR-10 为其预研基础）。依赖顺序：T4.1 → T4.2 → T4.3 → T4.4。

- [x] **T4.1 企业源路线调研**（1–2 人日）
  ⚠️ Azure Media Services 已于 2024-06-30 退役，README 与 PRD 中的 AMS 方案已不可用，需确定替代路线（候选：SharePoint 静态文件直接播放、Azure CDN + 自建 HLS 打包、第三方转码服务）。
  同时确认 SharePoint 权限模型与令牌（rtFa / FedAuth）生命周期；**调研结论需同步更新 PRD FR-7 与 README**。
  **结果（2026-11-07）：完成**（应「M4 路线调研先行」指示提前执行）。主路线定为 **A：SharePoint 静态文件 + Cookie 注入**（WebView 取 rtFa/FedAuth → Media3 `CookieHandler` → 直连 URL）；备选 A2：Graph `/drive/items/{id}/content` 换预认证短时 URL；ABR 演进 B：Blob 离线打包 HLS/DASH + CDN（微软官方 AMS 迁移工具即此形态）；Microsoft Stream 不开放第三方原生播放，排除。核实：AMS/Azure Media Player 均退役且无第一方替代；FedAuth ~10h（条件访问可至 ~1h）、rtFa 静默续期；权限复用 SharePoint 站点/库权限；单文件上限 250GB。决策记录：`docs/enterprise-source-research.md`；PRD 已升 v1.3，README Azure 节已加失效警告。

- [x] **T4.2 凭据播放链路**（3 人日）
  基于 FR-10 与 T4.1 路线 A：WebView 登录 → 提取 rtFa / FedAuth → 注入 Media3 `CookieHandler`（`CookieHandler.setDefault` 先于建播放器）→ ExoPlayer 播放 SharePoint 直连 URL；A2（Graph 预认证 URL）为备选。开放项实测：非交互拉流限流、条件访问对 WebView 登录的影响、两种 URL 形态的 Range 兼容。
  **结果（2026-11-08）：代码完成**。`SharePointCookieBridge` 从 WebView Cookie 存储提取 rtFa/FedAuth 写入 JVM `CookieManager`（`CookieHandler.setDefault` 于建播放器前就位）；实测确认 media3 1.11.1 `DefaultHttpDataSource` 已移除显式 CookieHandler 接口，但底层 `HttpURLConnection` 自动挂载默认 CookieHandler，故清单/Range/302 每跳全程带凭据（机制回写 README「鉴权直连的实现」节）。主界面加载 `*.sharepoint.com` 源时自动注入：有凭据直接播；无凭据弹窗引导去认证页（可「仍要播放」观察 401/403 路径）；重试前静默刷新凭据。真机验收（真实租户登录播放、限流、条件访问、URL 形态 Range 兼容）待设备，验收清单暂不勾。

- [ ] **T4.3 鉴权失败路径**（1 人日）
  401/403 明确提示（主因：FedAuth 过期或账号无站点权限）、引导回 WebView 重新登录刷新 Cookie；凭据不落明文日志（Cookie 全程不经日志输出）。

- [ ] **T4.4 集成成本与限制清单**（0.5 人日）
  权限模型、格式、缓存策略；作为 PRD FR-7 验收产出物。

**M4 完成门**：FR-7 验收清单全勾 + 成本清单入库。

---

## 执行顺序总览

```text
M1（T1.3 ✅；T1.1/T1.2/T1.4 真机项暂缓，待设备）
 └─ T2.1 ✅ → T2.5（迁移）✅ → T2.3 ✅ → T2.2 ✅ → T2.4 ✅
     └─ T3.1（需真机）→ T3.2 ✅（代码）→ T3.3（需模拟器）
         └─ T4.1 ✅（路线调研先行完成）→ T4.2 ✅（代码）→ T4.3 → T4.4
```

工作量合计约 **20 人日**（不含 T1.2 验收返工与 T4 调研引发的方案调整）。
