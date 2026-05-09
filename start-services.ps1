$services = @(
    @{ name="circleguard-auth-service";         port=8180 },
    @{ name="circleguard-identity-service";     port=8083 },
    @{ name="circleguard-gateway-service";      port=8087 },
    @{ name="circleguard-form-service";         port=8086 },
    @{ name="circleguard-promotion-service";    port=8088 },
    @{ name="circleguard-notification-service"; port=8082 }
)

foreach ($svc in $services) {
    Write-Host "▶ Levantando $($svc.name)..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", `
        "cd C:\dev\circle-guard-public; .\gradlew :services:$($svc.name):bootRun -Dorg.gradle.jvmargs='-Xmx256m' --no-daemon"
    Write-Host "⏳ Esperando 40s para que levante antes del siguiente..." -ForegroundColor Yellow
    Start-Sleep -Seconds 40
}
Write-Host "✅ Todos los servicios iniciados." -ForegroundColor Green
