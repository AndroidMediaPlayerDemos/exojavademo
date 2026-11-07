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

- [ ] **T1.3 分批提交与推送**（0.5 人日）
  建议按文件边界拆分：① AIDL 自托管（`MyService`、`aidl/`、manifest 服务与 `<queries>`、gradle `aidl`）② `MediaPlayerActivity` 及布局 ③ `WebViewActivity` 及布局 ④ 主界面入口改造（`activity_main.xml`、`strings.xml`）⑤ 文档（`PRD.md`、`TASKS.md`）。

- [ ] **T1.4 跨应用绑定验证**（0.5 人日，可选）
  安装 MediaPlayerWithSurfaceView 应用，按 `MainActivity` 注释切换绑定目标为外部包名，验证 `<queries>` 生效与跨应用调用。

**M1 完成门**：PRD 中 FR-4 / FR-9 / FR-10 验收清单全部勾选；main 分支 CI 绿灯。

---

## M2 播放能力回归

> 关联：FR-3 / FR-5 / 技术债。依赖顺序：T2.1 → T2.3 → T2.2 → T2.4。

- [ ] **T2.1 Media3 迁移评估（Spike）**（2 人日）
  对照 ExoPlayer 2.19.1 与 `androidx.media3` 的类名映射（`SimpleExoPlayer → androidx.media3.exoplayer.ExoPlayer` 等）、Gradle 依赖替换、已知行为差异；回归范围与工作量清单。
  产出：迁移决策记录（迁 / 不迁 / 何时迁），入库 `docs/` 或 README 附录；**决定 T2.2–T3.x 在哪条基线上实施**。

- [ ] **T2.3 FR-5 播放源管理**（2–3 人日）
  URL 输入、最近播放历史（SharedPreferences 足够）、预置示例源列表（渐进式 MP4 / HLS / DASH 各至少一条）、非法或不可达源 5 秒内报错。
  排在 T2.2 之前的原因：轨道选择需要多码率源，本任务的预置源列表直接提供。

- [ ] **T2.2 FR-3 轨道选择恢复**（2 人日）
  基于多码率源（T2.3 预置）实现画质/音轨选择与「自动」档（`TrackSelectionParameters`）；实现参照官方 Track Selection 文档，避免回到旧的全量遍历写法。

- [ ] **T2.4 技术债清理**（1 人日）
  `printStackTrace` 收敛为用户可见的错误提示；带宽实验（commit `5cdb4bf`）结论回写 README。

**M2 完成门**：FR-3 / FR-5 验收清单全勾；Media3 决策记录入库。

---

## M3 自适应流验证与体验增强

> 关联：FR-6 / FR-8 / 风险表第 5 条。

- [ ] **T3.1 FR-6 HLS/DASH/ABR 实测**（2 人日）
  复用 T2.3 预置源；限速工具模拟弱网，观察码率下调与恢复；实测结论回写 README 知识库。

- [ ] **T3.2 FR-8 播放体验增强**（3 人日）
  缓冲/错误态 UI 与一键重试、±10s 快进快退、横滑 seek、音量/亮度竖滑手势。

- [ ] **T3.3 最小仪器测试集**（2 人日）
  主流程 Espresso：起播、倍速切换、源切换。若 T2.1 决定迁移 Media3，本任务提前到迁移之前完成，作为迁移回归基线。

**M3 完成门**：FR-6 / FR-8 验收清单全勾；测试集在 CI 中运行。

---

## M4 企业源 PoC

> 关联：FR-7（FR-10 为其预研基础）。依赖顺序：T4.1 → T4.2 → T4.3 → T4.4。

- [ ] **T4.1 企业源路线调研**（1–2 人日）
  ⚠️ Azure Media Services 已于 2024-06-30 退役，README 与 PRD 中的 AMS 方案已不可用，需确定替代路线（候选：SharePoint 静态文件直接播放、Azure CDN + 自建 HLS 打包、第三方转码服务）。
  同时确认 SharePoint 权限模型与令牌（rtFa / FedAuth）生命周期；**调研结论需同步更新 PRD FR-7 与 README**。

- [ ] **T4.2 凭据播放链路**（3 人日）
  基于 FR-10：WebView 登录 → 提取 rtFa / FedAuth → 注入播放请求头 → ExoPlayer 播放受保护视频。

- [ ] **T4.3 鉴权失败路径**（1 人日）
  401/403 明确提示、重新登录与令牌刷新；凭据不落明文日志。

- [ ] **T4.4 集成成本与限制清单**（0.5 人日）
  权限模型、格式、缓存策略；作为 PRD FR-7 验收产出物。

**M4 完成门**：FR-7 验收清单全勾 + 成本清单入库。

---

## 执行顺序总览

```text
M1（T1.1 → T1.2 → T1.3 → T1.4可选）
 └─ T2.1 → T2.3 → T2.2 → T2.4
     └─ [若迁移] T3.3 提前 → T3.1 → T3.2
         └─ T4.1 → T4.2 → T4.3 → T4.4
```

工作量合计约 **20 人日**（不含 T1.2 验收返工与 T4 调研引发的方案调整）。
