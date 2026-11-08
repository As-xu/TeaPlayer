# TeaPlayer · Android App 骨架

> 本目录是 Gradle root。文档全在仓库根的 [`docs/`](../docs)：计划 [PLAN.md](../docs/PLAN.md) / [PLAN-v2-plugins.md](../docs/PLAN-v2-plugins.md) / [PLAN-v3-plugins.md](../docs/PLAN-v3-plugins.md) / [PLAN-v4-market.md](../docs/PLAN-v4-market.md)；UI 见 [`docs/ux/`](../docs/ux) 与可交互原型 [`docs/ux-prototype/index.html`](../docs/ux-prototype/index.html)。

## 模块（11 个，按 v2 §5 冻结）

```
:app(手机)   :tv(TV)                      wiring / 导航 / manifest / 进程声明
    └─────────┬─────────┘
           :feature                        Compose + VM + UI State
     ┌───────┬┴────────┬──────────────┬──────────────┐
:core:player :core:data :core:plugin :core:designsystem
                 │           │
                 │           └─► :core:plugin-runtime  ★ android:process=":plugin"
                 ├─► :core:domain ──► :core:model（纯 JVM）
                 └─► :core:net（SafeHttpDataSource，唯一出网）
```

依赖规则（Konsist 断言，尚未接入）：

1. `:core:model` / `:core:domain` 纯 JVM：禁 android / media3 / okhttp / rhino / hilt。
2. AIDL 只在 `:core:plugin`；`:core:domain` 只暴露 `PluginRuntimePort`（JSON + 基本类型）。
3. `:core:plugin-runtime` **不得**依赖 `:core:data` / `:core:player`（否则 `:plugin` 进程构造第二个 `SimpleCache` → 索引损坏，R-P0-1）。
4. 只有 `:core:net` 持有 OkHttp。
5. `:feature` 不 import `androidx.media3.*`、不 import Rhino。
6. 只有 `:core:plugin` 做 zip 解压与签名校验。

## 当前状态（2026-11-08）

- ✅ 11 个模块的空壳 + 依赖方向 + `:app`/`:tv` 两个 application（**同 `applicationId = com.teaplayer`**，`versionCode` 分段 1000/2000，D6）
- ✅ `:core:designsystem` 的 tokens（`TpColor`/`TpSpace`/`TpRadius`/`TpType` + `TeaPlayerTheme`，`dynamicColor=false`）——与 [`docs/design/tokens.md`](../docs/design/tokens.md) 一一对应
- ✅ `:core:model` 的 `SourceError`（全工程唯一一份，码与 [`docs/design/copy-errors.md`](../docs/design/copy-errors.md) 对齐）
- ✅ `:core:domain` 的 `CatalogSource` / `PlaybackTarget` / `PluginRuntimePort` / `SourceResult`
- ⬜ 尚未实现：SafeHttpDataSource、插件 Registry/Installer、搜索聚合、播放器、Room、市场
- ⬜ 未接入：Hilt（依赖已在 catalog，等第 1 个 VM 再开）、Konsist archtest、Compose UI（只有 Placeholder）

## 已实测（2026-11-08 21:30）

- `./gradlew :core:model:build :core:domain:build` ✅
- `./gradlew :app:assembleDebug` ✅ → `app-debug.apk` 9.0 MB
- `./gradlew :tv:assembleDebug` ✅ → `tv-debug.apk` 9.3 MB
- `:core:net` 单测 **11 项**、`:core:plugin` 单测 **13 项**，全绿
- 真机接上真实源：`暴风资源` 的 `ac=list` 拉回 20 条并渲染（截图 `docs/ux-prototype/shot-device-list.png`）
- 真机安装 + 启动 ✅（NOH-AN00 / Android 12），截图见 [`docs/ux-prototype/shot-device-mvp.png`](../docs/ux-prototype/shot-device-mvp.png)

踩到的三处环境坑（已修）：

1. 本机无 Gradle、无 `~/.gradle`：用腾讯镜像取 `gradle-8.13-bin.zip`（官方 CDN 被限速到 ~4 KB/s），解压到 `D:/DevEnv/gradle-8.13`，再用它生成 wrapper。
2. JDK 只有 21，`jvmToolchain(17)` 会失败 → 纯 JVM 模块改为「JDK 21 编译 + `jvmTarget=17`」。
3. `local.properties` 里 `sdk.dir` 用反斜杠转义会被解析错 → 改成正斜杠 `D:/DevEnv/Android/Sdk`。

## 构建

```bash
# 需要 JDK 17+（本机 21 可用）与 Android SDK（本机 D:/DevEnv/Android/Sdk，已写进 local.properties）
./gradlew :app:assembleDebug        # 手机
./gradlew :tv:assembleDebug         # TV
./gradlew :core:model:build         # 只验纯 JVM 层，最快冒烟
```

`local.properties` 与 `build/`、`.gradle/` 已在 `.gitignore` 中，不会入库。

## 版本口径

| 项 | 值 | 理由 |
|---|---|---|
| AGP | 8.7.3 | 与 compileSdk 35 匹配 |
| Kotlin | 2.0.21 | 需 `kotlin.plugin.compose`，已加入 catalog |
| minSdk | 26 | v1 决定（`Android 8.0` 起） |
| compileSdk / targetSdk | **35** | 已装 `platforms;android-35`（`printf "y\ny\ny\n" \| sdkmanager.bat --install`）。注意 `androidx.work:2.10.0` 强制要求 compileSdk ≥ 35，用 34 会直接失败在 `checkDebugAarMetadata` |
| Compose BOM | 2024.12.01 | 与 Kotlin 2.0.21 的 compose compiler 兼容 |
| 动态取色 | 关闭 | 会破坏 ok/warn/err 语义（tokens.md §5） |

## 下一批任务（按 P1 顺序）

1. `:core:net` 的 `SafeHttpDataSource`（唯一出网口：拒私网/回环、302 复检、体积上限）—— 安全红线 RB1，先于一切业务代码
2. `:core:plugin` 的 Registry + Installer（`.tpkg` 校验、sha256、TOFU pin）
3. `:core:data` 的 sources.json 种子读取 + Room 健康列
4. `:feature` 的 PH-1 首页 + PH-3 结果列表（先接 1 个种子源跑通）
