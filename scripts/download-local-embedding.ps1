[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$modelDirectory = Join-Path $projectRoot '.models\onnx'
New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null

$downloads = @(
  @{
    Name = 'tokenizer.json'
    Url = 'https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json'
    MinimumBytes = 10000000
  },
  @{
    Name = 'model_quantized.onnx'
    Url = 'https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx'
    MinimumBytes = 100000000
  }
)

foreach ($item in $downloads) {
  $target = Join-Path $modelDirectory $item.Name
  if ((Test-Path -LiteralPath $target) -and (Get-Item -LiteralPath $target).Length -ge $item.MinimumBytes) {
    Write-Host "$($item.Name) already available on D:." -ForegroundColor Green
    continue
  }
  $partial = "$target.part"
  Write-Host "Downloading $($item.Name) to D: ..." -ForegroundColor Cyan
  Invoke-WebRequest -Uri $item.Url -OutFile $partial -MaximumRedirection 10 -TimeoutSec 600
  if ((Get-Item -LiteralPath $partial).Length -lt $item.MinimumBytes) { throw "Downloaded $($item.Name) is unexpectedly small." }
  Move-Item -LiteralPath $partial -Destination $target -Force
}

Get-ChildItem -LiteralPath $modelDirectory -File |
  Where-Object { $_.Name -in @('tokenizer.json', 'model_quantized.onnx') } |
  Select-Object Name, Length |
  Format-Table -AutoSize
Write-Host 'Local multilingual embedding model is ready.' -ForegroundColor Green
