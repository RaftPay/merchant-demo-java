#!/usr/bin/env bash
set -euo pipefail

# ─── 颜色 ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}✔ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
fail() { echo -e "${RED}✘ $1${NC}"; exit 1; }

echo "=============================="
echo " RaftPay Java Demo - Bootstrap"
echo "=============================="
echo

# ─── 1. 检查 Java ───
if ! command -v java &>/dev/null; then
    fail "未检测到 Java，请先安装 JDK 8+"
fi
if ! command -v javac &>/dev/null; then
    fail "未检测到 javac，请先安装 JDK（非仅 JRE）"
fi

JAVA_VER=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')
ok "Java $JAVA_VER"

# ─── 2. 检查版本 >= 8 ───
MAJOR=$(echo "$JAVA_VER" | awk -F'.' '{if ($1 == 1) print $2; else print $1}')
if [ "$MAJOR" -lt 8 ]; then
    fail "需要 Java 8+，当前版本: $JAVA_VER"
fi
ok "Java 版本满足要求 (>= 8)"

# ─── 3. 编译所有 .java 文件 ───
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo
echo "编译 Java 源文件..."
javac -encoding UTF-8 "$SCRIPT_DIR"/*.java
ok "编译成功"

# ─── 4. 生成 config.properties ───
CONFIG_FILE="$SCRIPT_DIR/config.properties"
EXAMPLE_FILE="$SCRIPT_DIR/config.example.properties"

if [ -f "$CONFIG_FILE" ]; then
    warn "config.properties 已存在，跳过生成"
else
    if [ ! -f "$EXAMPLE_FILE" ]; then
        fail "找不到 config.example.properties"
    fi
    cp "$EXAMPLE_FILE" "$CONFIG_FILE"
    ok "已从模板生成 config.properties，请编辑填入真实凭证"
fi

echo
echo -e "${GREEN}环境就绪！${NC}"
echo "  1. 编辑 config.properties 填入商户凭证"
echo "  2. 运行示例：java -cp $SCRIPT_DIR Example"
echo "  3. 启动回调：java -cp $SCRIPT_DIR Callback"
