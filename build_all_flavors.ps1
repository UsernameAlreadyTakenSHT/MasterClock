# ChessClockV2 Multi-Flavor Build & Audit Script
# This script builds all release flavors and audits their sizes.

$flavors = @("complete", "standard", "light", "extraLight")
$results = @()

Write-Host "--- Starting Multi-Flavor Release Build ---" -ForegroundColor Cyan

foreach ($flavor in $flavors) {
    Write-Host "Building flavor: $flavor..." -ForegroundColor Yellow

    # Capitalize first letter for Gradle task name
    $flavorTask = $flavor.Substring(0,1).ToUpper() + $flavor.Substring(1)
    $taskName = ":app:assemble${flavorTask}Release"

    # Run Gradle build
    ./gradlew $taskName

    if ($LASTEXITCODE -eq 0) {
        # Locate APK
        $apkPath = "app/build/outputs/apk/$flavor/release/app-$flavor-release-unsigned.apk"
        if (-not (Test-Path $apkPath)) {
            # Fallback for signed or differently named APKs
            $apkPath = Get-ChildItem "app/build/outputs/apk/$flavor/release/*.apk" | Select-Object -ExpandProperty FullName -First 1
        }

        if (Test-Path $apkPath) {
            $size = (Get-Item $apkPath).Length / 1KB
            $results += [PSCustomObject]@{
                Flavor = $flavor
                Status = "Success"
                Size_KB = [math]::Round($size, 2)
            }
        }
    } else {
        $results += [PSCustomObject]@{
            Flavor = $flavor
            Status = "FAILED"
            Size_KB = 0
        }
    }
}

Write-Host "`n--- Build Summary ---" -ForegroundColor Cyan
$results | Format-Table -AutoSize

Write-Host "`nAPKs are located in: app/build/outputs/apk/" -ForegroundColor Gray
