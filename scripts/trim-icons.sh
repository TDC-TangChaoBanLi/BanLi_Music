#!/usr/bin/env bash
# 按当前源码实际使用的图标裁剪 material-icons-extended desktop jar。
# 用法：scripts/trim-icons.sh <input.jar> <output.jar>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT="${1:-}"
OUTPUT="${2:-}"

[[ -n "$INPUT" && -n "$OUTPUT" ]] || {
    echo "用法：scripts/trim-icons.sh <input.jar> <output.jar>" >&2
    exit 2
}
[[ -f "$INPUT" ]] || {
    echo "输入 jar 不存在：$INPUT" >&2
    exit 1
}

command -v jar >/dev/null 2>&1 || {
    echo "未找到 jar 命令，请使用 JDK 17+ 运行" >&2
    exit 1
}

USED_ICONS="$(
    grep -rhoE 'Icons(\.AutoMirrored)?\.Rounded\.[A-Za-z0-9_]+' \
        "$ROOT/shared/src" "$ROOT/androidApp/src" "$ROOT/desktopApp/src" \
        | sed -E 's/.*Rounded\.//' \
        | sort -u || true
)"
if [[ -z "$USED_ICONS" ]]; then
    echo "没有从源码中解析到图标引用，保留原 jar" >&2
    cp -f "$INPUT" "$OUTPUT"
    exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
EXTRACT="$WORK/extract"
mkdir -p "$EXTRACT"
(cd "$EXTRACT" && jar --extract --file "$INPUT")

ICONS_ROOT="$EXTRACT/androidx/compose/material/icons"
if [[ ! -d "$ICONS_ROOT" ]]; then
    echo "jar 中没有 material icons 目录，保留原 jar" >&2
    cp -f "$INPUT" "$OUTPUT"
    exit 0
fi

# 项目只使用 Rounded 与 AutoMirrored.Rounded；其他风格整目录移除。
find "$ICONS_ROOT" -mindepth 1 -maxdepth 1 -type d \
    ! -name rounded ! -name automirrored -exec rm -rf {} +
AUTO_ROOT="$ICONS_ROOT/automirrored"
if [[ -d "$AUTO_ROOT" ]]; then
    find "$AUTO_ROOT" -mindepth 1 -maxdepth 1 -type d \
        ! -name rounded -exec rm -rf {} +
fi

prune_style() {
    local dir="$1"
    [[ -d "$dir" ]] || return 0
    local file name
    while IFS= read -r -d '' file; do
        name="$(basename "$file" Kt.class)"
        if ! grep -qxF "$name" <<< "$USED_ICONS"; then
            rm -f "$file"
        fi
    done < <(find "$dir" -maxdepth 1 -type f -name '*Kt.class' -print0)
}

prune_style "$ICONS_ROOT/rounded"
prune_style "$AUTO_ROOT/rounded"

(
    cd "$EXTRACT"
    jar --create --file "$OUTPUT" --no-manifest .
)

BEFORE="$(stat -c%s "$INPUT")"
AFTER="$(stat -c%s "$OUTPUT")"
if [[ "$AFTER" -le 0 ]]; then
    echo "裁剪结果为空，保留原 jar" >&2
    cp -f "$INPUT" "$OUTPUT"
    exit 0
fi
echo "material-icons-extended: $BEFORE -> $AFTER bytes" >&2
