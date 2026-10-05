$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & javac -d out/dashboard src/ImageInspect.java src/VideoSource.java src/FrameProcessing.java src/AsciiDashboard.java
    if ($LASTEXITCODE -ne 0) { throw 'Dashboard compilation failed.' }
    & java -cp out/dashboard AsciiDashboard
} finally {
    Pop-Location
}
