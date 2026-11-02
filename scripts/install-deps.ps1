# 把 RapidOCRLite 官方 Java 绑定安装到本地 Maven 仓库（首次构建前执行一次）。
# 该 jar 未发布到 Maven 中央仓库，只随 GitHub Release 分发。
param(
    [string]$Version = "5.0.0a15"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "lib\rapidocrlite-java-$Version.jar"

if (-not (Test-Path $jar)) {
    $url = "https://github.com/RapidAI/RapidOCRLite/releases/download/v$Version/rapidocrlite-java-$Version.jar"
    Write-Host "下载 $url"
    New-Item -ItemType Directory -Force (Split-Path $jar) | Out-Null
    Invoke-WebRequest -Uri $url -OutFile $jar
}

Write-Host "安装到本地仓库: $jar"
mvn install:install-file `
    "-Dfile=$jar" `
    "-DgroupId=ai.rapidocr" `
    "-DartifactId=rapidocrlite-java" `
    "-Dversion=$Version" `
    "-Dpackaging=jar"

if ($LASTEXITCODE -ne 0) {
    throw "安装 rapidocrlite-java 失败"
}
Write-Host "完成: ai.rapidocr:rapidocrlite-java:$Version"
