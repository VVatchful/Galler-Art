$ErrorActionPreference = 'Stop'
$cliBuild = Join-Path $PSScriptRoot 'out/cli'
& javac -d $cliBuild (Join-Path $PSScriptRoot 'src/ImageInspect.java') (Join-Path $PSScriptRoot 'src/VideoSource.java') (Join-Path $PSScriptRoot 'src/FrameProcessing.java') (Join-Path $PSScriptRoot 'src/ConversionConfig.java') (Join-Path $PSScriptRoot 'src/AsciiCli.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $cliBuild AsciiCli @args
exit $LASTEXITCODE
