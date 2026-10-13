# docs/ci/ —— workflow 暂存区

约定借鉴自 [`TaCZ_Refabricated_Unofficial`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial)
的同名目录：**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端复制到
`.github/workflows/` 同名文件**（编辑现有文件或新建，粘贴、提交即可）。

为什么需要它：沙箱 Agent 的凭据是 GitHub App 安装令牌，**没有 `workflow` 权限** ——
对 `.github/workflows/` 下文件的推送会被 GitHub 拒绝（本仓库 2026-10-13 实测：
`refusing to allow a GitHub App to create or update workflow
.github/workflows/build.yml without workflows permission`）。本目录是绕开该限制的
唯一通道。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；上线后本目录的副本就是
正式件的镜像，下次改动继续在这里迭代。

## 待维护者手动上线（2026-10-13）

> **一次性动作**：把下面 4 个文件逐一复制到 `.github/workflows/` 同名路径（GitHub
> 网页端 Upload files 或编辑的粘贴均可），一次 commit 即可。上线后 arena 分支的
> push 就会自动跑 CI；沙箱 Agent 用 `gh api contents` 读回 `build-reports/compile-java.log`
> 迭代修编译错误（见本文件末尾）。

| 文件 | 动作 | 内容 |
|---|---|---|
| `compile-check.yml` | **新建** `.github/workflows/compile-check.yml` | 编译验证闭环（移植自 TaCZ v4）：GitHub Actions 编译 `compileJava compileClientJava`，arena/** 分支把 `build-reports/compile-java.log` 经 Contents API 写回本分支 —— 沙箱无 JDK、maven.* 直连不通，编译验证全靠这条闭环 |
| `build.yml` | **整体替换** `.github/workflows/build.yml` | 全量 `gradlew build` + jar artifact（14 天）+ 四个静态校验（mixin 配置完整性与注册性 / en-zh 语言键齐平 / 版本一致性 / 数据包 Tier-1 sanity） |
| `consistency.yml` | **新建** `.github/workflows/consistency.yml` | 版本号 ↔ README / fabric.mod.json / publish.yml 守门；arena/** 也触发 |
| `publish.yml` | **整体替换** `.github/workflows/publish.yml` | 与现正式件只差版本对齐：`MINECRAFT_VERSION` 26.1.2→26.2、`RELEASE_NAME` 封面同步（修 CURRENT_BASELINE 记录的 pending manual apply；版本一致性门禁会查它） |

> 上线时 4 个一起复制。若只上线前 3 个而忘了 `publish.yml`，`consistency.yml` /
> `build.yml` 的版本一致性检查会立刻报 publish.yml 漂移 —— 那是预期信号，不是误报。

## 配套脚本（已随仓库提交，无需手动操作）

- `scripts/check_release_consistency.sh` —— 版本一致性检查（`consistency.yml` 与
  `build.yml` 共用）。支持 `--strict` 把「Unofficial <mc> Port」封面文案检查升级为错误。
- `scripts/compat_check.py` —— 本仓数据包 Tier-1 离线检查（`build.yml` 调用）。

## 沙箱侧怎么用 compile-check 的日志回推

```bash
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<arena分支名>" \
  --jq .content | base64 -d
```

日志只保留头 200 行 + 尾 800 行，尾部有 `== job status: ... ==` 标记。
`build-reports/` 已加入 `.gitignore`（日志由 CI 经 API 提交，不进本地工作区）。

## 在新 workflow 上线之前，沙箱还能看什么

正式件里遗留的旧 `build.yml`（`on: [pull_request, push]` 全分支触发）仍会在每次
push 时跑 `compat_check.py` + `./gradlew build` 并上传 jar artifact。沙箱可以通过
`gh run list` / `gh run view`（API 通道）看红绿与失败步骤；但 Actions 日志 blob
域在沙箱不可达，看不到编译错误细节 —— 所以完整闭环仍需上线 `compile-check.yml`。
