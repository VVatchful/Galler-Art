$ErrorActionPreference = 'Stop'
# Windows builds linked from https://ffmpeg.org/download.html.
$ffmpegRoot = Join-Path $PSScriptRoot 'tools/ffmpeg'
$ffmpegStage = Join-Path $PSScriptRoot ('out/ffmpeg-setup-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $ffmpegStage | Out-Null
$ffmpegArchive = Join-Path $ffmpegStage 'ffmpeg.zip'
Invoke-WebRequest -Uri 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' -OutFile $ffmpegArchive -TimeoutSec 180
Expand-Archive -LiteralPath $ffmpegArchive -DestinationPath $ffmpegStage
$ffmpegBinary = Get-ChildItem -LiteralPath $ffmpegStage -Filter ffmpeg.exe -Recurse | Select-Object -First 1
if (-not $ffmpegBinary) { throw 'The downloaded archive did not contain ffmpeg.exe.' }
New-Item -ItemType Directory -Force -Path (Join-Path $ffmpegRoot 'bin') | Out-Null
foreach ($ffmpegName in @('ffmpeg.exe', 'ffprobe.exe')) {
    Copy-Item -LiteralPath (Join-Path $ffmpegBinary.Directory.FullName $ffmpegName) -Destination (Join-Path $ffmpegRoot ('bin/' + $ffmpegName))
}
$ffmpegDistribution = $ffmpegBinary.Directory.Parent.FullName
Get-ChildItem -LiteralPath $ffmpegDistribution -File | Copy-Item -Destination $ffmpegRoot
Write-Output "Installed FFmpeg and ffprobe in $ffmpegRoot. Download retained in $ffmpegStage."
