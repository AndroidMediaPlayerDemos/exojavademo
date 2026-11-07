# T2.1 · Media3 迁移评估（决策记录）

| 项 | 内容 |
|---|---|
| 任务 | TASKS.md T2.1（Spike，2 人日预算） |
| 日期 | 2026-11-07 |
| 结论 | **建议迁移**，目标 `androidx.media3 1.11.1`，在 M2 的 FR-3 / FR-5 开工前执行 |
| 预估工作量 | 1–1.5 人日（依赖与构建链 0.5 + 代码改造 0.5 + 回归 0.5） |

## 1. 动因

当前播放内核 ExoPlayer `2.19.1`（2023-06 发布）属于旧的 `com.google.android.exoplayer2` 体系。官方自 Media3 1.0（2023-03）起将 ExoPlayer 迁入 `androidx.media3`，2.x 不再发布功能更新，缺陷与安全修复仅进 Media3。继续在 2.x 上开发 FR-3（轨道选择）/ FR-5（源管理）/ FR-6（HLS/DASH）会加深锁定，迁移成本随功能增多只增不减。

## 2. 版本选型

| 候选 | 结论 |
|---|---|
| **1.11.1**（2026-09-10，最新 stable） | ✅ 采用 |
| 1.12.0-alpha02（2026-10-26） | ❌ 不用 alpha |
| 1.7.0 | ❌ 官方误发布的 stable（实为 alpha 代码，公告要求弃用；如路过此区间用 1.7.1+） |

兼容性核对：Media3 自 1.9.0 起 minSdk 23（1.12 起 24），本项目 minSdk 24 ✅。

## 3. 依赖与构建链变更

```text
移除  com.google.android.exoplayer:exoplayer:2.19.1
移除  com.google.android.exoplayer:exoplayer-ui:2.19.1
新增  androidx.media3:media3-exoplayer:1.11.1
新增  androidx.media3:media3-ui:1.11.1
（FR-6 时另加 media3-exoplayer-hls / media3-exoplayer-dash——
  2.x 的聚合 artifact 含 HLS/DASH，Media3 拆分为独立模块，勿漏）
```

⚠️ **连带升级（评估的主要发现）**：Media3 1.11.1 的 AAR 元数据要求 compileSdk 36（评估时按 1.5 时代资料估的 35，实测 `checkDebugAarMetadata` 报错后修正）。因此需同步：

| 组件 | 原值 | 目标值（实测落地） |
|---|---|---|
| compileSdk | 34 | 36 |
| AGP | 8.3.0 | 8.10.1（compileSdk 36 需 8.9.1+） |
| Gradle wrapper | 8.4 | 8.11.1（AGP 8.10 最低要求） |
| targetSdk | 34 | 34（不动） |
| JDK | 17 | 17（不动，CI 已就位；本地 21 亦可） |

## 4. 代码改动清单（本项目实际用到的 API）

全部为包名替换，方法签名在本项目用法下不变：

| 2.19.1 | Media3 1.11.1 |
|---|---|
| `com.google.android.exoplayer2.SimpleExoPlayer` | `androidx.media3.exoplayer.ExoPlayer`（接口 + `ExoPlayer.Builder`） |
| `com.google.android.exoplayer2.MediaItem` | `androidx.media3.common.MediaItem` |
| `com.google.android.exoplayer2.PlaybackParameters` | `androidx.media3.common.PlaybackParameters` |
| `com.google.android.exoplayer2.ui.PlayerView` | `androidx.media3.ui.PlayerView` |
| 布局 `activity_main.xml` 中的 `<com.google.android.exoplayer2.ui.PlayerView>` | `<androidx.media3.ui.PlayerView>` |

涉及文件：`MainActivity.java`、`app/build.gradle.kts`、`activity_main.xml`，共 3 个。`SpeedAdapter` / `MyService` / `MediaPlayerActivity` / `WebViewActivity` 不受影响。

## 5. 风险与回归点

- **构建链升级是主要风险**（AGP/Gradle 升级可能引发无关告警），代码替换本身机械
- Media3 1.11 起「动态调度」默认开启（播放工作循环节拍变化），对 Demo 场景无可感知影响，真机回归时留意起播与倍速
- `NotificationPermission` lint 抑制先保留：Media3 同样含 DownloadService 代码路径，迁移后再评估是否可移除
- 迁移后旧 2.x 依赖体积移除，APK 变小，无 ProGuard 影响（minify 关闭）

## 6. 执行清单（迁移落地进度）

- [x] 升级 Gradle wrapper → AGP → compileSdk（3399b5c 先行一步，随后按 AAR 元数据要求调整为 Gradle 8.11.1 / AGP 8.10.1 / compileSdk 36）
- [x] 替换依赖坐标（media3-exoplayer / media3-ui 1.11.1）
- [x] `MainActivity.java`、`activity_main.xml` 包名替换（全仓已无 `exoplayer2` 引用）
- [x] `assembleDebug` 通过（本地 Gradle 8.11.1 + JDK 21）；CI 绿灯随提交验证
- [ ] 真机过 PRD FR-1/FR-2 验收清单（起播、倍速、旋转）——并入 T1.1 一并做。另注：原默认源 TearsOfSteel.mp4 所在 gtv-videos-bucket 已整桶 403（2026-11 实测），真机回归前需先完成 T2.3 换默认源
- [ ] 更新 PRD 6 章（播放层版本）与本文档状态

## 7. 参考

- Media3 版本与发布说明：https://developer.android.com/jetpack/androidx/releases/media3
- ExoPlayer → Media3 迁移指南：https://developer.android.com/media/media3/exoplayer/migration-guide
