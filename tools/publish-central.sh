#!/usr/bin/env bash
# Maven Central Publisher Portal 上传（发布运维）。
# 决策依据与凭据约定见 docs/alignment/release-decision-go-2026-11-04.zh-CN.md。
#
# 用法:
#   tools/publish-central.sh [version] [publishingType]
#     version          默认取 gradle.properties 的 lkanvas.version（版本单一来源）
#     publishingType   USER_MANAGED（默认：Portal 网页点击 Publish 确认）
#                      | AUTOMATIC（校验通过后自动发布）
#
# 前置:
#   1. ./gradlew publish 已把目标版本发到 build/maven-repo（含 .asc 签名伴随件）
#   2. Central Portal（central.sonatype.com）命名空间已完成验证
#   3. 凭据写入 local.properties（gitignored，严禁提交 gradle.properties）:
#        central.username=<Portal User Token 的 username>
#        central.password=<Portal User Token 的 token>
#
# 安全约定: 凭据不回显、不落日志；中间产物放 mktemp 目录，退出自动清理。
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION_ARG="${1:-}"
PUBLISHING_TYPE="${2:-USER_MANAGED}"

if [[ -z "$VERSION_ARG" ]]; then
  VERSION_ARG="$(grep '^lkanvas.version=' gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')"
fi
if [[ -z "$VERSION_ARG" ]]; then
  echo "错误: 无法确定版本（传参或检查 gradle.properties 的 lkanvas.version）" >&2
  exit 1
fi
# groupId 单一来源（发布契约），仓库路径 = groupId 中的 . 换 /（如 io/github/iamkings）
GROUP_ID="$(grep '^lkanvas.groupId=' gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')"
if [[ -z "$GROUP_ID" ]]; then
  echo "错误: gradle.properties 缺少 lkanvas.groupId（Maven Central 已验证命名空间）" >&2
  exit 1
fi
GROUP_PATH="${GROUP_ID//./\/}"
case "$PUBLISHING_TYPE" in
  USER_MANAGED|AUTOMATIC) ;;
  *) echo "错误: publishingType 只能是 USER_MANAGED 或 AUTOMATIC，收到 '$PUBLISHING_TYPE'" >&2; exit 1 ;;
esac

read_cred() {
  local key="$1" value=""
  if [[ -f local.properties ]]; then
    value="$(grep "^${key}=" local.properties | head -1 | cut -d= -f2- | tr -d '\r')"
  fi
  if [[ -z "$value" ]]; then
    # 与 publishing.gradle.kts 的环境变量回退同形：CENTRAL_USERNAME / CENTRAL_PASSWORD
    local env_key
    env_key="$(printf '%s' "$key" | tr '[:lower:].' '[:upper:]_')"
    value="${!env_key:-}"
  fi
  printf '%s' "$value"
}

CENTRAL_USERNAME="$(read_cred central.username)"
CENTRAL_PASSWORD="$(read_cred central.password)"
if [[ -z "$CENTRAL_USERNAME" || -z "$CENTRAL_PASSWORD" ]]; then
  echo "错误: 缺少凭据——在 local.properties 配置 central.username / central.password（Central Portal User Token）" >&2
  exit 1
fi

# 目标版本目录必须齐全（仅上传目标版本，历史版本目录不带入 bundle）
STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
mkdir -p "$STAGING/$GROUP_PATH"
for artifact in lkanvas-core lkanvas-compose; do
  dir="build/maven-repo/${GROUP_PATH}/${artifact}/${VERSION_ARG}"
  if [[ ! -d "$dir" ]]; then
    echo "错误: 缺少 $dir —— 先执行 ./gradlew publish 生成制品" >&2
    exit 1
  fi
  if ! ls "$dir"/*.asc >/dev/null 2>&1; then
    echo "错误: $dir 无 .asc 签名伴随件——Central 校验必失败。" >&2
    echo "       检查 local.properties 的 signing.keyId/password/secretKeyRingFile 后重跑 ./gradlew publish" >&2
    exit 1
  fi
  mkdir -p "$STAGING/$GROUP_PATH/$artifact"
  cp -R "$dir" "$STAGING/$GROUP_PATH/$artifact/"
done

(cd "$STAGING" && zip -qr bundle.zip "$GROUP_PATH")
BUNDLE_SIZE="$(du -h "$STAGING/bundle.zip" | cut -f1 | tr -d ' ')"
echo "上传目标: $GROUP_ID 版本 $VERSION_ARG (publishingType=$PUBLISHING_TYPE, bundle=$BUNDLE_SIZE)"

# Portal API: Bearer = base64(username:token)
BASIC_TOKEN="$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | openssl base64 -A)"
UPLOAD_URL="https://central.sonatype.com/api/v1/publisher/upload?publishingType=${PUBLISHING_TYPE}"
RESPONSE="$(curl -sS -X POST "$UPLOAD_URL" \
  -H "Authorization: Bearer ${BASIC_TOKEN}" \
  -H "Accept: application/json" \
  -F "bundle=@${STAGING}/bundle.zip;type=application/zip")"

DEPLOYMENT_ID="$RESPONSE"
if [[ ! "$DEPLOYMENT_ID" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "错误: 上传未返回 deployment id（Portal 拒绝，响应如下）" >&2
  echo "$RESPONSE" >&2
  exit 1
fi
echo "已受理: deploymentId=$DEPLOYMENT_ID"

STATUS_URL="https://central.sonatype.com/api/v1/publisher/status?id=${DEPLOYMENT_ID}"
# 哨兵初值：空状态（Portal 5xx 等）也必须首轮可见，不得静默跳过
LAST_STATE="__INIT__"
MAX_POLLS=160   # 15s x 160 = 40min
for _ in $(seq 1 "$MAX_POLLS"); do
  sleep 15
  STATUS_JSON="$(curl -sS --max-time 20 "$STATUS_URL" -H "Authorization: Bearer ${BASIC_TOKEN}" -H "Accept: application/json")"
  STATE="$(printf '%s' "$STATUS_JSON" | sed -n 's/.*"deploymentState":"\([^"]*\)".*/\1/p')"
  if [[ "$STATE" == "$LAST_STATE" ]]; then
    continue
  fi
  case "$STATE" in
    PENDING_VALIDATION|VALIDATING)
      echo "[$(date +%H:%M:%S)] Portal 校验中…"
      ;;
    VALIDATED)
      echo "[$(date +%H:%M:%S)] 校验通过，进入发布队列"
      ;;
    WAITING_FOR_USER)
      echo "[$(date +%H:%M:%S)] 等待人工确认: 打开 https://central.sonatype.com -> View Deployments -> 对该次上传点击 Publish"
      ;;
    PUBLISHING)
      echo "[$(date +%H:%M:%S)] 正式发布中…"
      ;;
    PUBLISHED)
      echo "[OK] 已发布: $GROUP_ID:lkanvas-core / $GROUP_ID:lkanvas-compose : $VERSION_ARG"
      echo "     Central 检索: https://central.sonatype.com/artifact/$GROUP_ID/lkanvas-compose/$VERSION_ARG"
      exit 0
      ;;
    FAILED)
      echo "[FAIL] 校验/发布失败，Portal 响应:" >&2
      echo "$STATUS_JSON" >&2
      echo "   明细见 https://central.sonatype.com -> View Deployments" >&2
      exit 1
      ;;
    "")
      # beta03 发布实测：Portal status 5xx 时空状态分支触发 bash 对「$VAR）」
      # 多字节邻接的解析缺陷（变量名吞入后续 UTF-8 字节报未绑定）。展开与
      # 多字节字符解耦并设默认值，语义不变。
      SNIP="$(printf '%s' "${STATUS_JSON:-}" | head -c 160 | tr '\n' ' ')"
      echo "[$(date +%H:%M:%S)] 状态暂不可读，继续轮询，原始响应:"
      printf '%s\n' "$SNIP"
      ;;
    *)
      echo "[$(date +%H:%M:%S)] 状态: $STATE"
      ;;
  esac
  LAST_STATE="$STATE"
done

echo "错误: 轮询超时（约 40 分钟），请到 Portal View Deployments 页面人工跟进 deploymentId=$DEPLOYMENT_ID" >&2
exit 1
