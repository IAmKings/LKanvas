# 命名治理：仓库/目录/PRD 统一为 LKanvas

## Context（根因与现状）

项目实际品牌、代码、构建坐标**早已全部是 LKanvas**（`rootProject.name="lkanvas"`、包名 `io.lkanvas.*`、Maven `io.lkanvas`、README/DESIGN 标题均为 LKanvas），但仓库与目录仍沿用旧名 KKLineChart。根因：**PRD 从未回填定名**——`docs/PRD-KLineChart-Kotlin-Compose.zh-CN.md:7` 至今写「项目**暂定名** LKanvas；最终名称及 Maven groupId 在 M0 确定」，名字未定稿即建仓，沿用了上游名。

由此产生的 4 处真实残留（已全量扫描确认，源码/gradle/CI/脚本零残留）：
1. **PRD 定名状态未回填**（根因）
2. **GitHub 远程** `IAmKings/KKLineChart`（public；DESIGN.md:5 声明「品牌沿用仓库公开名称 LKanvas」与实际矛盾）
3. **本地目录** `…/ai_workspace/KKLineChart`
4. **POM scm 指向不存在的** `github.com/lkanvas/lkanvas`（gradle/publishing.gradle.kts）

附带：约 160 处人工文档绝对路径链接（README 1、performance-baseline 8、compatibility 3、release-acceptance-r2-60 1、docs/alignment 18 文件 149 处）；evidence 内 2,250 处机器日志路径。

**已确认的决策**：GitHub 改名 `IAmKings/LKanvas`；本地目录改名由我在最后一步执行；纯命名文件单独提交、与在途修改（0.15.1-rc5）重叠的文件不提交。

## 实施步骤

### 1. PRD 定名回填（根因修正）— `docs/PRD-KLineChart-Kotlin-Compose.zh-CN.md`
- 元数据表：`文档版本 0.1 → 0.2（2026-11-03 项目定名与仓库更正记录）`；`项目暂定名 | LKanvas；最终名称及 Maven groupId 在 M0 确定` → `项目定名 | **LKanvas**（M0 确定；Maven groupId io.lkanvas；仓库/目录/GitHub/品牌统一使用）`
- 元数据表下新增「命名说明」短小节：上游参考项目为 KLineChart（klinecharts/KLineChart v10.1.0），本产品定名 **LKanvas**，两者不得混用；记录 2026-11-03 仓库/目录更正（此处保留旧名一次，作为更正记录）。文件名不改（其中 KLineChart 指上游，合理）。

### 2. DESIGN.md 措辞收尾 — `DESIGN.md:5`
「品牌沿用仓库公开名称 **LKanvas**，不使用目录名 KKLineChart 作为产品名称」→ 改为已完成状态：品牌名 LKanvas，仓库与目录名已于 2026-11-03 统一。

### 3. 人工文档绝对路径 → 仓库相对路径
- 一次性 Python 脚本（放 /tmp，不入库）：对 `](<仓库绝对路径>/<rest>)` 形态的链接，按每个文档自身位置计算 `os.path.relpath` 替换；同时报告非链接形态的裸路径供人工确认。
- 范围：README.md、docs/performance-baseline.md、docs/compatibility.md、docs/release-acceptance-r2-60.md、docs/alignment/*.md（执行时以 grep 实际结果为准）。
- **docs/evidence/**（168 文件）保持原样不改写——证据是当时构建日志原文，改写即篡改证据。

### 4. POM scm 对齐 — `gradle/publishing.gradle.kts`
scm 连接与 URL 改为实际地址：`https://github.com/IAmKings/LKanvas` / `scm:git:git://github.com/IAmKings/LKanvas.git`。下次发布生效；0.15.1-rc5 尚未外发，无外部制品引用旧地址。

### 5. 防复发护栏 — `tools/alignment/check.py`
新增命名一致性检查（与现有 check 风格一致）：扫描全部 `.md`（**排除 docs/evidence/**）及 `*.gradle.kts`/`gradle.properties`，大小写不敏感匹配 `kk[-_ ]?line`；按文件白名单允许计数（PRD=1 处更正记录，其余=0），超限即报错并提示正确名称。纯工具改动，不触碰冻结基线代码。

### 6. plan-session 台账登记 — `.zcode/plans/`（执行时先读现有格式）
按现有验收轮记录格式追加一条命名治理条目（原因、决策、更正内容）。

### 7. 验证
- `grep -rniI -e 'kkline' -e 'kk-line' --exclude-dir=.git`：预期仅 PRD 更正记录 1 处 + docs/evidence/ 原样保留，其余 0 命中
- `python3 tools/alignment/check.py` 通过（含新护栏）
- `./gradlew projects` 冒烟确认 settings 完整
- 脚本改写前后 diff 抽查若干链接可达性（相对路径指向的文件存在）

### 8. 提交（一笔，纯命名文件）
仅提交：PRD、DESIGN.md、绝对路径被清理且无在途修改的 alignment 文档、check.py、plan-session 文件。**不提交**与在途修改重叠的文件（README.md、gradle/publishing.gradle.kts、docs/performance-baseline.md、docs/compatibility.md 及执行时 git status 显示有在途改动的文件）——它们随你的下一次 0.15.1-rc5 验收提交入库。提交信息（沿用中文风格）：`命名治理：项目定名 LKanvas——PRD 定名回填、文档路径相对化、POM scm 对齐、check.py 命名护栏`。

### 9. GitHub 仓库改名（外向操作，按已确认选择）
`gh repo rename LKanvas -R IAmKings/KKLineChart`，随后 `git remote set-url origin https://github.com/IAmKings/LKanvas.git`，`git ls-remote origin` 验证。旧地址 GitHub 自动重定向，已有克隆链接不断。

### 10. 本地目录改名（最后一步，按已确认选择）
`mv /Users/pauldeman/Documents/ai_workspace/KKLineChart /Users/pauldeman/Documents/ai_workspace/LKanvas`。此后本会话路径失效——你需要从新路径重新打开 ZCode 会话与 Android Studio（gradle 缓存自动重建，无需手改）；若该工作区配置过定时自动化，需在新路径下重建。完成后我会给出收尾说明。

## 验收标准
- 全仓库（除 evidence 与 PRD 更正记录）grep 旧名零命中；check.py 含护栏全绿
- GitHub 仓库、本地目录、git remote、POM scm、PRD 定名五处一致为 LKanvas/lkanvas 体系
- 纯命名文件已单独成笔提交；在途 WIP 未被混入

## 执行记录（2026-11-03）

**结果：步骤 1–9 全部完成，步骤 10（本地目录 mv）紧随本记录执行。**

| 步骤 | 结果 |
|---|---|
| 1. PRD 定名回填 | ✅ 文档版本 0.1→0.2；「暂定名」→「定名 LKanvas（Maven io.lkanvas）」；新增「命名说明」小节含 2026-11-03 更正记录（旧名在产品文件中的唯一保留处） |
| 2. DESIGN.md 措辞 | ✅ 第 5 行改为完成态：仓库与目录已于 2026-11-03 统一为 LKanvas |
| 3. 绝对路径相对化 | ✅ 572 处 markdown 链接（23 个人工文档）绝对路径→仓库相对路径；docs/evidence（168 文件）证据原文未动；复验 621 条相对链接有效，54 条 MISSING 均为转换前已存在（PRD 预期链接、审计报告引用 /tmp 临时目录、历史改名文件） |
| 4. POM scm | ✅ publishing.gradle.kts scm → github.com/IAmKings/LKanvas（该文件为在途修改，随 0.15.1-rc5 验收提交入库） |
| 5. check.py 护栏 | ✅ validate_naming()：扫描 md（docs/evidence 豁免）+ *.gradle.kts/gradle.properties，正则 k{2}[-_ ]?line；PRD 白名单 1 次。正测通过；负测（临时文件含旧名）确认 exit=1 拦截；正则自测 10 例无误报（不误伤 KLineChart/lkanvas 单 K 名） |
| 7. 验证 | ✅ 终扫：产品文件旧名仅 PRD 1 处（白名单）；check.py exit=0；gradlew projects 正常（root 'lkanvas' + 4 模块） |
| 8. 提交 | ✅ c37125d「命名治理（1/2）」13 个文件（PRD + 11 审计文档 + check.py）；release-acceptance-r2-60.md 为未跟踪在途文件，已从本笔移出 |
| 9. GitHub 改名 | ✅ IAmKings/KKLineChart → IAmKings/LKanvas（公开库，旧地址自动重定向）；本地 remote 已更新，git ls-remote 连通 |
| 10. 本地目录 | ✅ 目标名被原始规划目录占用，经用户确认「规划目录让位」：ai_workspace/LKanvas（10-23~25 的 PRD/可行性资料）→ LKanvas-planning，随后仓库 KKLineChart → LKanvas。本地目录与 GitHub 仓库名一致；需从新路径重开 ZCode 会话与 IDE |

**交接说明**：README、publishing.gradle.kts、performance-baseline、compatibility、DESIGN.md、demo-checklist、round2–7 报告、plan-session 等在途文件中的命名修正已就位于工作区，随下一次 0.15.1-rc5 验收提交入库（建议提交信息标注「命名治理（2/2）」）。check.py 护栏扫描范围不含 .zcode/（agent 工作笔记）与 docs/evidence/（历史证据），旧名回流产品文件时会报错拦截。
