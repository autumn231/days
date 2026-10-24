#!/usr/bin/env bash
#
# 本地（构建服务器）一键构建并发布 release 包。
#
#   ./scripts/release.sh              # 构建 + 打 tag + 发布到 GitHub Release
#   ./scripts/release.sh --build-only # 只构建，产物在 dist/
#   ./scripts/release.sh --draft      # 以 draft 形式发布
#
# 约定：
#   * 只产出 arm64-v8a（v8a）单一架构的 APK
#   * 强制 release 构建（R8 混淆 + 资源压缩），绝不发布 debug 包
#   * Release Notes 从 CHANGELOG.md 提取 "## v<versionName>" 段落
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# ---------- 环境 ----------
export JAVA_HOME="${JAVA_HOME:-/data/home/tools/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/data/home/tools/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
APK="app/build/outputs/apk/release/app-release.apk"
TARGET_ABI="arm64-v8a"

BUILD_ONLY=false
DRAFT_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --build-only) BUILD_ONLY=true ;;
    --draft) DRAFT_ARGS+=(--draft) ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# ---------- 读取版本 ----------
VERSION="$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)"
VERSION_CODE="$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)"
TAG="v${VERSION}"
DIST_DIR="dist"
DIST_APK="${DIST_DIR}/days-${TAG}-${TARGET_ABI}.apk"

echo "==> 版本 ${TAG} (versionCode ${VERSION_CODE})"

if [ ! -f keystore.properties ]; then
  echo "!! 未找到 keystore.properties，将使用 debug 签名（仅用于自测，不要发布）"
fi

# ---------- 构建 ----------
echo "==> clean assembleRelease（R8 开启）"
./gradlew clean assembleRelease --no-daemon --console=plain

if [ ! -f "$APK" ]; then
  echo "!! 构建结束但没有找到 $APK"
  exit 1
fi

# ---------- 校验 ----------
echo "==> 校验产物"
BADGING="$("$BUILD_TOOLS/aapt2" dump badging "$APK")"
echo "$BADGING" | grep -E "^package:|^sdkVersion|^targetSdkVersion|^native-code" || true

ABIS="$(echo "$BADGING" | grep -oP "native-code: '\K[^']+" || true)"
if [ "$ABIS" != "$TARGET_ABI" ]; then
  echo "!! ABI 不符合预期：期望 ${TARGET_ABI}，实际 '${ABIS}'"
  exit 1
fi

if [ ! -f "app/build/outputs/mapping/release/mapping.txt" ]; then
  echo "!! 没有生成 mapping.txt，R8 可能未生效"
  exit 1
fi

echo "==> 签名信息"
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | head -4

mkdir -p "$DIST_DIR"
cp "$APK" "$DIST_APK"
echo "==> 产物：${DIST_APK} ($(du -h "$DIST_APK" | cut -f1))"

if $BUILD_ONLY; then
  echo "==> --build-only，跳过发布"
  exit 0
fi

# ---------- Release Notes ----------
NOTES_FILE="$(mktemp)"
if [ -f CHANGELOG.md ]; then
  awk -v ver="v${VERSION}" '
    /^## v/ {
      if (in_section) exit
      if (index($0, "## " ver)) { in_section = 1; next }
    }
    in_section { print }
  ' CHANGELOG.md > "$NOTES_FILE"
fi
if [ ! -s "$NOTES_FILE" ]; then
  printf '## 更新内容\n\n本版本详细更新请见 [CHANGELOG.md](CHANGELOG.md)。\n' > "$NOTES_FILE"
fi

{
  echo "> 本地构建 · ${TARGET_ABI} · R8 已开启 · $(date '+%Y-%m-%d %H:%M')"
  echo
  cat "$NOTES_FILE"
} > "${NOTES_FILE}.final"

# ---------- 发布 ----------
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "!! tag ${TAG} 已存在，请先提升 versionName/versionCode"
  exit 1
fi

git tag -a "$TAG" -m "days ${TAG}" 2>/dev/null || true
git push origin "$TAG"

echo "==> 创建 GitHub Release"
gh release create "$TAG" "$DIST_APK" \
  --title "倒数日 ${VERSION}" \
  --notes-file "${NOTES_FILE}.final" \
  --latest \
  "${DRAFT_ARGS[@]+"${DRAFT_ARGS[@]}"}"

echo "==> 完成：$(gh release view "$TAG" --json url -q .url)"
