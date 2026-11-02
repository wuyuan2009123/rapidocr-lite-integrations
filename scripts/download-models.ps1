# 下载 PP-OCRv6 模型到 models/ 目录。
# 用法: .\scripts\download-models.ps1 -Size tiny   # tiny | small | medium
param(
    [ValidateSet("tiny", "small", "medium")]
    [string]$Size = "tiny"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$models = Join-Path $root "models"
New-Item -ItemType Directory -Force $models | Out-Null

$base = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0"
$files = @(
    @{ Name = "PP-OCRv6_det_$Size.onnx"; Url = "$base/onnx/PP-OCRv6/det/PP-OCRv6_det_$Size.onnx" },
    @{ Name = "PP-OCRv6_rec_$Size.onnx"; Url = "$base/onnx/PP-OCRv6/rec/PP-OCRv6_rec_$Size.onnx" },
    @{ Name = "ppocrv6_${Size}_dict.txt"; Url = "$base/paddle/PP-OCRv6/rec/PP-OCRv6_rec_$Size/ppocrv6_${Size}_dict.txt" }
)

foreach ($f in $files) {
    $target = Join-Path $models $f.Name
    if (Test-Path $target) {
        Write-Host "已存在，跳过: $($f.Name)"
        continue
    }
    Write-Host "下载 $($f.Name)"
    Invoke-WebRequest -Uri $f.Url -OutFile $target
}

Write-Host "模型已就绪: $models"
