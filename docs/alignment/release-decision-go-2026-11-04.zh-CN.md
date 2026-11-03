# 远程发布 GO 决策（0.16.0-beta01 内测首发）

> **当前发布决策：GO（2026-11-04，解除远程发布 HOLD）**。本文为当前有效的发布决策记录；
> 历史轮次报告（[release-readiness-round7](release-readiness-round7-execution-2026-11-02.zh-CN.md)、
> [pre-release-final-acceptance-round3](pre-release-final-acceptance-round3-2026-11-01.zh-CN.md)、
> targeted-fix-retest-round4~6 等）的 HOLD 结论**保留原意不改写**，其「发布仍 HOLD」表述由本文件统一覆盖与推进。

## 1. 决策

| 项 | 决定 |
|---|---|
| 发布决策 | **GO**：解除远程发布 HOLD，制品发布至 Maven Central，开启内测 |
| 内测版本 | `0.16.0-beta01`（pre-release 语义；后续 rc → 正式按标准路径切版） |
| 坐标 | `io.lkanvas:lkanvas-compose` / `io.lkanvas:lkanvas-core`（groupId 保持不变） |
| 渠道 | Maven Central Publisher Portal（central.sonatype.com），命名空间 `io.lkanvas` 经域名 DNS TXT 验证 |
| 发布方式 | `./gradlew publish` → 本地制品（含 .asc 签名）→ `tools/publish-central.sh` 上传，默认 `USER_MANAGED`（Portal 网页点击 Publish 确认） |
| 决策人 | 项目维护者（KingsLZ），2026-11-04 |

## 2. 依据与接受的风险（K60U 未达标项转技术债）

多轮真机验收（第三~七轮，双机 K60U/PJZ110）后，正确性、链路、兼容、无障碍、R8、字体/方向等项已收敛；
仅剩 K60U 以下帧指标未达标，**维护者决策（2026-11-04）接受为已知缺口，转为技术债跟踪**：

| 项 | 实测（K60U） | 门槛 | 来源 |
|---|---|---|---|
| S0 实时帧 CPU P95 | 19.258404ms | ≤16ms | round7 执行报告 §7.2 |
| S0 正 overrun | 53/486=10.905350% | ≤5% | round7 执行报告 §7.2 |
| S2 压力正 overrun | 60/480=12.5% | ≤10% | round7 执行报告 §7.2 |

PJZ110 对应项短测通过；双机最终双次矩阵、真机 heap/B06 尾项与真人 TalkBack 正式认证随技术债轮补齐。
内测反馈 issues 作为输入之一纳入后续「更严苛性能优化轮」的排期。

## 3. 内测范围与后续排期

- **内测**：制品经 Maven Central 公开获取；接入方式见 README「内测接入」章节；反馈入口 GitHub Issues
  （`bug_report` / `compatibility_report` 模板）。
- **后续轮 1（性能）**：K60U S0/S2 收敛 → 双机最终矩阵（S0/S1/S2 双次、输入双用例、cold/warm、容量、真机 heap、
  30 分钟 B06）→ 真人 TalkBack 正式认证 → 重新对齐 performance-baseline 门槛结论并关闭豁免序列。
- **后续轮 2（反馈）**：内测 issues 分诊（缺陷修复 / 对齐差异 / 期望能力），随 0.16.0 rc 序列收口。

## 4. 发布运维约定

- **版本单一来源**：`gradle.properties` 的 `lkanvas.version`；`verifyLibraryVersions` 守护任务挂 `publish*` 全族，
  校验 `LKanvas.VERSION`、`LKanvasChart.VERSION`、app-sample versionName、consumer 示例依赖与之一致，不一致即构建失败。
- **凭据**：只放 `local.properties`（gitignored）或环境变量，**严禁写入会提交的 gradle.properties**：
  - Central Portal User Token：`central.username` / `central.password`
  - GPG：`signing.keyId` / `signing.password` / `signing.secretKeyRingFile`
    （导出旧格式密钥环：`gpg --export-secret-keys <KEYID> > ~/.gnupg/secring.gpg`）
  - 凭据缺位时发布链路跳过签名（仅限本地制品）；`tools/publish-central.sh` 遇无 `.asc` 直接拒绝上传。
- **上传**：`tools/publish-central.sh [version] [USER_MANAGED|AUTOMATIC]`——只打包目标版本目录（历史版本不带入），
  Bearer=base64(username:token)，状态轮询至 PUBLISHED/FAILED；首版走 USER_MANAGED 人工确认。
- **伴随件**：core JAR / compose AAR + sources + javadoc stub（`gradle/javadoc-stub.html`；dokka 正式文档后续补）+
  POM（url/license/scm/developer 齐备）+ Gradle `.module` + `.asc` 签名。

## 5. 验收证据（本决策落地的链路验证）

- 全量单测（core+compose）、`:lkanvas-compose:assembleRelease`、本地 publish `0.16.0-beta01`（制品/签名/伴随件齐备）、
  A-46 独立 consumer 仅凭制品编译——数据见 [compatibility.md 变更记录 2026-11-04 行](../compatibility.md)。
- 远端验证（仅 `mavenCentral()` 的干净 consumer 解析 `0.16.0-beta01` 编译）在 Portal 发布上线后补记至 compatibility.md。
