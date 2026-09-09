# ChessClockV2 Multi-Flavor Build & Audit Script
# This script builds all release flavors and audits their sizes.

# paper is in this list, and it was not.
#
# The script audits what a release ships, and a release ships five APKs. Building only the four app
# flavors meant it printed "Success" for everything while never compiling the paper module at all --
# so a change that broke the E-Ink build passed the very script whose job is to catch that. Both the
# buildAllReleaseFlavors Gradle task and AGENTS.md's verification command have always included it.
$flavors = @("complete", "standard", "lite", "mini", "paper")
$results = @()

Write-Host "--- Starting Multi-Flavor Release Build ---" -ForegroundColor Cyan

foreach ($flavor in $flavors) {
    Write-Host "Building flavor: $flavor..." -ForegroundColor Yellow

    # paper is its own module rather than a flavor of :app, so it has its own task and its own
    # output path below.
    if ($flavor -eq "paper") {
        $taskName = ":paper:assembleRelease"
    } else {
        # Capitalize first letter for Gradle task name
        $flavorTask = $flavor.Substring(0,1).ToUpper() + $flavor.Substring(1)
        $taskName = ":app:assemble${flavorTask}Release"
    }

    # Run Gradle build
    ./gradlew $taskName

    if ($LASTEXITCODE -eq 0) {
        # Locate APK
        if ($flavor -eq "paper") {
            $apkGlob = "paper/build/outputs/apk/release/*.apk"
            $apkPath = Get-ChildItem $apkGlob -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName -First 1
        } else {
            $apkGlob = "app/build/outputs/apk/$flavor/release/*.apk"
            $apkPath = "app/build/outputs/apk/$flavor/release/app-$flavor-release-unsigned.apk"
            if (-not (Test-Path $apkPath)) {
                # Fallback for signed or differently named APKs
                $apkPath = Get-ChildItem $apkGlob -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName -First 1
            }
        }

        # An empty $apkPath would make Test-Path throw rather than answer, which used to leave a
        # built-but-unlocated flavor missing from the table with no row at all.
        if ($apkPath -and (Test-Path $apkPath)) {
            $size = (Get-Item $apkPath).Length / 1KB
            $results += [PSCustomObject]@{
                Flavor = $flavor
                Status = "Success"
                Size_KB = [math]::Round($size, 2)
            }
        } else {
            $results += [PSCustomObject]@{
                Flavor = $flavor
                Status = "Built, APK not found"
                Size_KB = 0
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

Write-Host "`nApp APKs: app/build/outputs/apk/  --  paper APK: paper/build/outputs/apk/release/" -ForegroundColor Gray
