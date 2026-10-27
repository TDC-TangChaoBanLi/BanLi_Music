#!/usr/bin/env bash
# 按需打包 BanLiMusic Linux 桌面端：便携版、deb、rpm，可选 jlink 裁剪 runtime。
# Windows 产物请使用 scripts/package-windows.ps1 在 Windows 上打包。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION=""
DIST="$ROOT/dist"
BUILD_DIR="$ROOT/build/desktop-package"
DO_BUILD=1
USE_JLINK=0
KEEP_TEMP=0
CP_FILE=""
LINUX_JDK=""
EXTRA_PATH=""
JLINK_MODULES="java.base,java.desktop,java.instrument,java.logging,java.prefs,jdk.unsupported,jdk.crypto.ec,jdk.zipfs"
RAW_TARGETS=()
TARGETS=()
LINUX_TARGETS=()
BUILT_FILES=()
LINUX_APP_IMAGE_READY=0
LINUX_RUNTIME_READY=0
NOJRE_ROOT_READY=0

usage() {
    cat <<'EOF'
用法：scripts/package-linux.sh [选项] <目标...>

目标（可重复，也可用逗号分隔）：
  linux-app-image       Linux 带 runtime 的 app-image 目录
  linux-tar             Linux 带 runtime 的 tar.gz
  linux-deb             Linux 带 runtime 的 deb
  linux-rpm             Linux 带 runtime 的 rpm
  linux-nojre-tar       Linux 无 runtime 的 tar.gz（需要 Java 17+）
  linux-nojre-deb       Linux 无 runtime 的 deb（依赖 Java 17+）
  linux-nojre-rpm       Linux 无 runtime 的 rpm（依赖 Java 17+）
  all-linux             上述全部 Linux 目标
  all                   同 all-linux

选项：
  --version <v>          版本号，默认读取 androidApp/module.yaml 的 versionName
  --dist <dir>           输出目录，默认 dist
  --jlink                使用 jlink 裁剪 runtime（只影响带 runtime 的目标）
  --no-jlink             使用完整 JDK runtime（默认）
  --jlink-modules <list> 覆盖 jlink 模块列表
  --skip-build           不运行 ./kotlin build -v release -m desktopApp
  --classpath-file <f>   使用包含 -cp 行的文件（例如 build/temp/java-args-*.txt）
  --linux-jdk <dir>      Linux JDK 目录（默认 JAVA_HOME 或 jpackage 所在 JDK）
  --extra-path <dir>     临时加入 PATH，例如本地的 fakeroot/rpmbuild 包装器
  --keep-temp           保留输入 jar 等中间目录
  -h, --help             显示帮助

示例：
  scripts/package-linux.sh --jlink all-linux
  scripts/package-linux.sh --jlink linux-tar linux-nojre-deb
  scripts/package-linux.sh --skip-build --jlink linux-app-image
EOF
}

die() {
    echo "错误：$*" >&2
    exit 1
}

note() {
    echo "==> $*" >&2
}

add_target() {
    local t="$1" existing
    for existing in "${TARGETS[@]:-}"; do
        [[ "$existing" == "$t" ]] && return 0
    done
    TARGETS+=("$t")
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --version) VERSION="${2:-}"; shift 2 ;;
        --dist) DIST="${2:-}"; shift 2 ;;
        --jlink) USE_JLINK=1; shift ;;
        --no-jlink) USE_JLINK=0; shift ;;
        --jlink-modules) JLINK_MODULES="${2:-}"; shift 2 ;;
        --skip-build) DO_BUILD=0; shift ;;
        --classpath-file) CP_FILE="${2:-}"; shift 2 ;;
        --linux-jdk) LINUX_JDK="${2:-}"; shift 2 ;;
        --extra-path) EXTRA_PATH="${2:-}"; shift 2 ;;
        --keep-temp) KEEP_TEMP=1; shift ;;
        -h|--help) usage; exit 0 ;;
        -*) die "未知选项：$1（使用 --help 查看帮助）" ;;
        *) RAW_TARGETS+=("$1"); shift ;;
    esac
done

if [[ ${#RAW_TARGETS[@]} -eq 0 ]]; then
    usage
    die "至少指定一个打包目标"
fi

if [[ -n "$EXTRA_PATH" ]]; then
    export PATH="$EXTRA_PATH:$PATH"
fi

if [[ -z "$VERSION" ]]; then
    VERSION=$(sed -n 's/.*versionName:[[:space:]]*"\(.*\)".*/\1/p' "$ROOT/androidApp/module.yaml" | head -1)
fi
[[ -n "$VERSION" ]] || die "无法读取版本号，请用 --version 指定"

for raw in "${RAW_TARGETS[@]}"; do
    IFS=',' read -r -a parts <<< "$raw"
    for part in "${parts[@]}"; do
        [[ -n "$part" ]] && add_target "$part"
    done
done

for t in "${TARGETS[@]}"; do
    case "$t" in
        all|all-linux)
            add_target linux-app-image; add_target linux-tar; add_target linux-deb; add_target linux-rpm
            add_target linux-nojre-tar; add_target linux-nojre-deb; add_target linux-nojre-rpm
            ;;
    esac
done

for t in "${TARGETS[@]}"; do
    case "$t" in
        all|all-linux) continue ;;
        linux-app-image|linux-tar|linux-deb|linux-rpm|linux-nojre-tar|linux-nojre-deb|linux-nojre-rpm)
            LINUX_TARGETS+=("$t") ;;
        windows-*|all-windows)
            die "Windows 目标不在此脚本中；请在 Windows 上运行 scripts/package-windows.ps1" ;;
        *) die "未知目标：$t" ;;
    esac
done

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "未找到 $1。$2"
}

if [[ -z "$LINUX_JDK" ]]; then
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then
        LINUX_JDK="$JAVA_HOME"
    elif command -v jpackage >/dev/null 2>&1; then
        LINUX_JDK="$(dirname "$(dirname "$(readlink -f "$(command -v jpackage)")")")"
    else
        die "未找到 jpackage，请安装 JDK 17+ 或用 --linux-jdk 指定 JDK 目录"
    fi
fi
[[ -x "$LINUX_JDK/bin/jpackage" ]] || die "$LINUX_JDK 下没有 jpackage"

mkdir -p "$DIST" "$BUILD_DIR"

if (( DO_BUILD )); then
    note "构建 desktopApp release 产物"
    (cd "$ROOT" && ./kotlin build -v release -m desktopApp)
fi

cp_valid() {
    local cp="$1" p
    [[ -n "$cp" ]] || return 1
    local IFS=':'
    for p in $cp; do
        [[ -f "$p" ]] || return 1
    done
    [[ "$cp" == *"/desktopApp-jvm.jar"* && "$cp" == *"/shared-jvm.jar"* ]]
}

read_cp_from_file() {
    local file="$1"
    awk 'prev=="-cp"{print; exit} {prev=$0}' "$file"
}

capture_classpath() {
    require_command setsid "无法安全地后台启动桌面端"
    local marker log pid args_file i
    marker=$(mktemp)
    touch -d '1 second ago' "$marker"
    log="$BUILD_DIR/classpath-run.log"
    note "启动一次桌面端以获取运行时 classpath（窗口可能短暂出现）"
    setsid "$ROOT/kotlin" run -m desktopApp --no-compose-hot-reload >"$log" 2>&1 &
    pid=$!
    args_file=""
    for ((i = 0; i < 240; i++)); do
        args_file=$(find "$ROOT/build/temp" -maxdepth 1 -name 'java-args-*.txt' -newer "$marker" -print -quit 2>/dev/null || true)
        [[ -n "$args_file" ]] && break
        if ! kill -0 "$pid" 2>/dev/null; then
            break
        fi
        sleep 0.5
    done
    kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    rm -f "$marker"
    [[ -n "$args_file" ]] || die "无法获取 classpath，请检查 $log 或用 --classpath-file 指定"
    read_cp_from_file "$args_file"
}

get_classpath() {
    local cp=""
    if [[ -n "$CP_FILE" ]]; then
        [[ -f "$CP_FILE" ]] || die "classpath 文件不存在：$CP_FILE"
        cp=$(read_cp_from_file "$CP_FILE")
        [[ -n "$cp" ]] || cp=$(head -1 "$CP_FILE")
        cp_valid "$cp" || die "classpath 文件中的路径无效：$CP_FILE"
        printf '%s' "$cp"
        return
    fi
    local latest=""
    latest=$(ls -t "$ROOT"/build/temp/java-args-*.txt 2>/dev/null | head -1 || true)
    if [[ -n "$latest" ]]; then
        cp=$(read_cp_from_file "$latest")
    fi
    if ! cp_valid "$cp"; then
        cp=$(capture_classpath)
    fi
    cp_valid "$cp" || die "classpath 无效"
    printf '%s' "$cp"
}

CLASSPATH_STR=$(get_classpath)

copy_jars() {
    local cp="$1" dest="$2" j base stem ext alt n
    mkdir -p "$dest"
    local -A seen=()
    local IFS=':'
    for j in $cp; do
        base=$(basename "$j")
        if [[ -n "${seen[$base]:-}" ]]; then
            if cmp -s "$j" "$dest/$base"; then
                continue
            fi
            stem="${base%.*}"
            ext=".${base##*.}"
            n=2
            while [[ -e "$dest/${stem}-dup${n}${ext}" ]]; do
                n=$((n + 1))
            done
            alt="${stem}-dup${n}${ext}"
            note "检测到同名不同内容的 jar，保留为 $alt"
            cp -f "$j" "$dest/$alt"
        else
            cp -f "$j" "$dest/$base"
            seen[$base]=1
        fi
    done
}

INPUT_LINUX="$BUILD_DIR/input-linux"
note "准备 Linux 打包输入 jar"
rm -rf "$INPUT_LINUX"
copy_jars "$CLASSPATH_STR" "$INPUT_LINUX"
rm -f "$INPUT_LINUX"/*windows-x64*.jar
[[ -f "$INPUT_LINUX/desktopApp-jvm.jar" ]] || die "缺少 desktopApp-jvm.jar"
compgen -G "$INPUT_LINUX/*skiko-awt-runtime-linux-x64*.jar" >/dev/null || \
    die "Linux 输入缺少 skiko-awt-runtime-linux-x64；请在 Linux 上构建 desktopApp"

build_linux_runtime() {
    (( LINUX_RUNTIME_READY )) && return
    local rt="$BUILD_DIR/runtime-linux" compress=2
    if "$LINUX_JDK/bin/jlink" --help 2>&1 | grep -q 'zip-'; then
        compress=zip-6
    fi
    local -a args=()
    if [[ -d "$LINUX_JDK/jmods" ]]; then
        args+=(--module-path "$LINUX_JDK/jmods")
    fi
    note "jlink 生成 Linux runtime（$JLINK_MODULES）"
    rm -rf "$rt"
    "$LINUX_JDK/bin/jlink" "${args[@]}" \
        --add-modules "$JLINK_MODULES" \
        --strip-debug --no-header-files --no-man-pages \
        --compress="$compress" \
        --output "$rt"
    "$rt/bin/java" -version >/dev/null 2>&1 || die "jlink runtime 无法运行"
    LINUX_RUNTIME_READY=1
}

jpackage_linux() {
    local type="$1" dest="$2" input="$3"
    shift 3
    local -a args=(
        --type "$type"
        --name BanLiMusic
        --app-version "$VERSION"
        --input "$input"
        --main-jar desktopApp-jvm.jar
        --main-class com.banlimusic.desktop.MainKt
        --icon "$ROOT/shared/composeResources/drawable/banli_logo.png"
        --dest "$dest"
    )
    if (( USE_JLINK )); then
        build_linux_runtime
        args+=(--runtime-image "$BUILD_DIR/runtime-linux")
    fi
    "$LINUX_JDK/bin/jpackage" "${args[@]}" "$@"
}

build_linux_app_image() {
    (( LINUX_APP_IMAGE_READY )) && return
    note "生成 Linux app-image"
    rm -rf "$BUILD_DIR/BanLiMusic"
    jpackage_linux app-image "$BUILD_DIR" "$INPUT_LINUX"
    LINUX_APP_IMAGE_READY=1
}

write_nojre_launcher() {
    local path="$1"
    cat > "$path" <<'EOF'
#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "未找到 Java，请安装 Java 17 或更高版本。" >&2
  exit 1
fi

JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 \
  | awk -F'"' '/version/ {print $2}' \
  | awk -F. '{print ($1 == 1 ? $2 : $1)}')
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then
  echo "需要 Java 17 或更高版本，当前为 ${JAVA_MAJOR:-未知}。" >&2
  exit 1
fi

exec "$JAVA_BIN" -cp "$APP_HOME/libs/*" com.banlimusic.desktop.MainKt "$@"
EOF
    chmod 755 "$path"
}

write_nojre_readme() {
    local path="$1"
    cat > "$path" <<EOF
BanLiMusic Desktop $VERSION (Linux x64 免安装版)

启动方式：
  ./bin/BanLiMusic

本目录不包含 Java 运行时，需要系统已安装 Java 17 或更高版本。
如果提示未找到 Java，请安装 OpenJDK（例如 Ubuntu/Debian: sudo apt install openjdk-17-jre）。

数据位置：
- 设置/收藏/历史/下载记录：~/.config/BanLiMusic（遵循 XDG_CONFIG_HOME）
- 缓存：~/.cache/BanLiMusic
- 默认下载目录：~/Music/BanLiMusic

音乐数据来自 GD 音乐台，仅供学习参考，请勿用于商业用途。
EOF
}

build_nojre_linux_bundle() {
    local bundle="$BUILD_DIR/nojre-linux/bundle/BanLiMusic"
    note "生成 Linux no-jre 便携版"
    rm -rf "$BUILD_DIR/nojre-linux/bundle"
    mkdir -p "$bundle/bin" "$bundle/libs"
    cp -f "$INPUT_LINUX"/*.jar "$bundle/libs"/
    write_nojre_launcher "$bundle/bin/BanLiMusic"
    write_nojre_readme "$bundle/README.txt"
}

build_nojre_linux_root() {
    (( NOJRE_ROOT_READY )) && return
    local root="$BUILD_DIR/nojre-linux/root"
    note "生成 Linux no-jre 安装包目录"
    rm -rf "$BUILD_DIR/nojre-linux/root"
    mkdir -p \
        "$root/opt/banlimusic-nojre/libs" \
        "$root/opt/banlimusic-nojre/bin" \
        "$root/usr/bin" \
        "$root/usr/share/applications" \
        "$root/usr/share/icons/hicolor/256x256/apps" \
        "$root/usr/share/doc/banlimusic-nojre" \
        "$root/DEBIAN"
    cp -f "$INPUT_LINUX"/*.jar "$root/opt/banlimusic-nojre/libs"/
    write_nojre_launcher "$root/opt/banlimusic-nojre/bin/banlimusic-nojre"
    ln -s /opt/banlimusic-nojre/bin/banlimusic-nojre "$root/usr/bin/banlimusic-nojre"
    cp "$ROOT/shared/composeResources/drawable/banli_logo.png" \
        "$root/usr/share/icons/hicolor/256x256/apps/banlimusic-nojre.png"
    cat > "$root/usr/share/applications/banlimusic-nojre.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=BanLiMusic (Java 17+)
GenericName=Music Player
Comment=Music client with GD Music source (requires Java 17+)
Exec=banlimusic-nojre
Icon=banlimusic-nojre
Terminal=false
Categories=Audio;Music;Player;
StartupWMClass=BanLiMusic
EOF
    cat > "$root/usr/share/doc/banlimusic-nojre/README.txt" <<EOF
BanLiMusic Desktop $VERSION (Linux x64, 需要系统 Java 17+)

启动方式：
  banlimusic-nojre
  或从应用菜单打开 “BanLiMusic (Java 17+)”

本包不包含 Java 运行时，安装时会由包管理器安装 Java 17+。
Debian/Ubuntu 可安装 java17-runtime / java21-runtime，
Fedora/RHEL 可安装 java-17-openjdk-headless。

数据位置：
- 设置/收藏/历史/下载记录：~/.config/BanLiMusic（遵循 XDG_CONFIG_HOME）
- 缓存：~/.cache/BanLiMusic
- 默认下载目录：~/Music/BanLiMusic

音乐数据来自 GD 音乐台，仅供学习参考，请勿用于商业用途。
EOF
    local installed_size
    installed_size=$(du -sk "$root/opt" "$root/usr" | awk '{s+=$1} END {print s}')
    cat > "$root/DEBIAN/control" <<EOF
Package: banlimusic-nojre
Version: $VERSION
Section: sound
Priority: optional
Architecture: amd64
Maintainer: BanLiMusic <noreply@banlimusic.app>
Installed-Size: $installed_size
Depends: java17-runtime | java21-runtime | java25-runtime
Description: BanLiMusic desktop client (requires Java 17+)
 BanLiMusic is a music client using the GD Music source.
 This package does not bundle a Java runtime; install Java 17 or newer.
EOF
    NOJRE_ROOT_READY=1
}

build_nojre_rpm() {
    require_command rpmbuild "请安装 rpm/rpm-build，或用 --extra-path 提供 rpmbuild"
    build_nojre_linux_root
    local rt="$BUILD_DIR/nojre-rpm"
    local root="$BUILD_DIR/nojre-linux/root"
    note "生成 Linux no-jre rpm"
    rm -rf "$rt"
    mkdir -p "$rt/SOURCES" "$rt/SPECS" "$rt/BUILD" "$rt/BUILDROOT" "$rt/RPMS" "$rt/SRPMS"
    cp -a "$root/." "$rt/SOURCES/tree/"
    rm -rf "$rt/SOURCES/tree/DEBIAN"
    cat > "$rt/SPECS/banlimusic-nojre.spec" <<EOF
%global debug_package %{nil}
%global __os_install_post %{nil}

Name:           banlimusic-nojre
Version:        $VERSION
Release:        1
Summary:        BanLiMusic desktop client (requires Java 17+)
License:        Unspecified
BuildArch:      x86_64
Requires:       java-headless >= 17
AutoReqProv:    no

%description
BanLiMusic is a music client using the GD Music source.
This package does not bundle a Java runtime; install Java 17 or newer.

%prep

%build

%install
mkdir -p %{buildroot}
cp -a %{_sourcedir}/tree/. %{buildroot}/

%files
/opt/banlimusic-nojre
/usr/bin/banlimusic-nojre
/usr/share/applications/banlimusic-nojre.desktop
/usr/share/icons/hicolor/256x256/apps/banlimusic-nojre.png
/usr/share/doc/banlimusic-nojre

%changelog
* $(LC_ALL=C date +'%a %b %d %Y') BanLiMusic <noreply@banlimusic.app> - $VERSION-1
- Release $VERSION
EOF
    rpmbuild -bb "$rt/SPECS/banlimusic-nojre.spec" --define "_topdir $rt"
    local rpm
    rpm=$(find "$rt/RPMS" -name "banlimusic-nojre-$VERSION-1.x86_64.rpm" -print -quit)
    [[ -n "$rpm" ]] || die "rpmbuild 未生成预期产物"
    cp -f "$rpm" "$DIST/"
    BUILT_FILES+=("$DIST/$(basename "$rpm")")
}

for t in "${LINUX_TARGETS[@]}"; do
    case "$t" in
        linux-app-image)
            build_linux_app_image
            ;;
        linux-tar)
            build_linux_app_image
            out="$DIST/BanLiMusic-$VERSION-linux-x64.tar.gz"
            note "生成 $out"
            tar -czf "$out" -C "$BUILD_DIR" BanLiMusic
            BUILT_FILES+=("$out")
            ;;
        linux-deb)
            require_command fakeroot "jpackage deb 需要 fakeroot"
            require_command dpkg-deb "jpackage deb 需要 dpkg-deb"
            note "生成 Linux deb"
            jpackage_linux deb "$DIST" "$INPUT_LINUX" \
                --linux-shortcut --linux-menu-group Audio \
                --linux-package-name banlimusic \
                --linux-deb-maintainer "noreply@banlimusic.app"
            BUILT_FILES+=("$DIST/banlimusic_${VERSION}_amd64.deb")
            ;;
        linux-rpm)
            require_command rpmbuild "jpackage rpm 需要 rpmbuild"
            note "生成 Linux rpm"
            jpackage_linux rpm "$DIST" "$INPUT_LINUX" \
                --linux-shortcut --linux-menu-group Audio \
                --linux-package-name banlimusic
            BUILT_FILES+=("$DIST/banlimusic-$VERSION-1.x86_64.rpm")
            ;;
        linux-nojre-tar)
            build_nojre_linux_bundle
            out="$DIST/BanLiMusic-$VERSION-linux-x64-nojre.tar.gz"
            note "生成 $out"
            tar -czf "$out" -C "$BUILD_DIR/nojre-linux/bundle" BanLiMusic
            BUILT_FILES+=("$out")
            ;;
        linux-nojre-deb)
            require_command dpkg-deb "生成 deb 需要 dpkg-deb"
            build_nojre_linux_root
            out="$DIST/banlimusic-nojre_${VERSION}_amd64.deb"
            note "生成 $out"
            dpkg-deb --build --root-owner-group "$BUILD_DIR/nojre-linux/root" "$out" >/dev/null
            BUILT_FILES+=("$out")
            ;;
        linux-nojre-rpm)
            build_nojre_rpm
            ;;
    esac
done

if (( ! KEEP_TEMP )); then
    rm -rf "$INPUT_LINUX"
fi

note "生成校验和"
(
    cd "$DIST"
    names=()
    for f in *"$VERSION"*; do
        [[ -f "$f" && "$f" != SHA256SUMS-* ]] && names+=("$f")
    done
    if [[ ${#names[@]} -gt 0 ]]; then
        printf '%s\n' "${names[@]}" | sort -u | while IFS= read -r name; do
            sha256sum "$name"
        done > "SHA256SUMS-$VERSION.txt"
    fi
)

note "完成。产物目录：$DIST"
