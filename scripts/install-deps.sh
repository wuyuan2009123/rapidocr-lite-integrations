#!/bin/sh
# 把 RapidOCRLite 官方 Java 绑定安装到本地 Maven 仓库（首次构建前执行一次）。
# 该 jar 未发布到 Maven 中央仓库，只随 GitHub Release 分发。
set -e

VERSION="${1:-5.0.0a15}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/lib/rapidocrlite-java-$VERSION.jar"

if [ ! -f "$JAR" ]; then
    URL="https://github.com/RapidAI/RapidOCRLite/releases/download/v$VERSION/rapidocrlite-java-$VERSION.jar"
    echo "下载 $URL"
    mkdir -p "$(dirname "$JAR")"
    if command -v curl >/dev/null 2>&1; then
        curl -fSL -o "$JAR" "$URL"
    else
        wget -O "$JAR" "$URL"
    fi
fi

echo "安装到本地仓库: $JAR"
mvn install:install-file \
    -Dfile="$JAR" \
    -DgroupId=ai.rapidocr \
    -DartifactId=rapidocrlite-java \
    -Dversion="$VERSION" \
    -Dpackaging=jar

echo "完成: ai.rapidocr:rapidocrlite-java:$VERSION"
