# 运行 RapidOCRLite CPU 压测（见 bench/OcrBench.java，结果写入 README）
param(
    [string]$Image = "samples/ch_en_num.jpg",
    [int]$Rounds = 3,
    [int]$Iters = 30,
    [int]$Seconds = 5
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $repo = (& mvn -q help:evaluate "-Dexpression=settings.localRepository" "-DforceStdout" | Out-String).Trim()
    if (-not $repo) {
        $repo = Join-Path $env:USERPROFILE ".m2\repository"
    }
    $jar = Join-Path $repo "ai\rapidocr\rapidocrlite-java\5.0.0a15\rapidocrlite-java-5.0.0a15.jar"
    if (-not (Test-Path $jar)) {
        throw "找不到 rapidocrlite-java，先执行 scripts/install-deps.ps1"
    }

    java "--enable-native-access=ALL-UNNAMED" "-Dstdout.encoding=UTF-8" `
        -cp $jar bench\OcrBench.java `
        $Image models\PP-OCRv6_det_tiny.onnx models\PP-OCRv6_rec_tiny.onnx models\ppocrv6_tiny_dict.txt `
        $Rounds $Iters $Seconds
}
finally {
    Pop-Location
}
