# JuYuMao 发布脚本
# 用法: .\release.ps1 -Version "4.1.0" -ReleaseNotes "修复了xxx"
# 功能: 更新版本号 → 追加更新日志 → 编译 debug APK → 打包到桌面 → 提交 → 推送 → 创建 GitHub Release(附 APK + 更新日志)

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [string]$ReleaseNotes
)

$ErrorActionPreference = "Stop"
# 仓库根 = 脚本所在目录（迁移后不再硬编码旧路径 D:\RuanJian\KaiFa\Juyumao）
$repo = $PSScriptRoot
$github = "ShaoCI-Hz/JuYuMao"
$desktop = [Environment]::GetFolderPath('Desktop')
$apkName = "JuYuMao-v$Version.apk"
$apkPath = Join-Path $desktop $apkName
$tokenFile = Join-Path $repo ".gh_token"

# ── 0. 输入校验 + 读取 token ──
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "版本号格式错误（应为 x.y.z）：$Version" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $tokenFile)) {
    Write-Host "缺少 token 文件 .gh_token（首次运行请创建，内容为 GitHub PAT）" -ForegroundColor Red
    exit 1
}
$token = (Get-Content $tokenFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host ".gh_token 内容为空" -ForegroundColor Red
    exit 1
}

# ── 1. 环境 ──
$env:JAVA_HOME = "D:\RuanJian\Java"
$env:ANDROID_HOME = "D:\RuanJian\Android tool\Android SDK"
$env:GITHUB_TOKEN = $token
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

try {
    # ── 2. 更新版本号 ──
    $gradleFile = Join-Path $repo "app\build.gradle.kts"
    $gradleContent = Get-Content $gradleFile -Raw -Encoding UTF8
    if ($gradleContent -notmatch 'versionCode = (\d+)') {
        Write-Host "未找到 versionCode，中止发布" -ForegroundColor Red
        exit 1
    }
    $newCode = [int]$Matches[1] + 1
    $gradleContent = $gradleContent -replace 'versionCode = \d+', "versionCode = $newCode"
    $gradleContent = $gradleContent -replace 'versionName = "[\d.]+"', "versionName = `"$Version`""
    [System.IO.File]::WriteAllText($gradleFile, $gradleContent, [System.Text.UTF8Encoding]::new($false))
    Write-Host "版本更新: versionCode=$newCode, versionName=$Version" -ForegroundColor Green

    # ── 3. 更新 README 更新日志（(?m) 使 ^ 匹配每行开头，README 中段的 '## 更新日志' 才能命中） ──
    $readmeFile = Join-Path $repo "README.md"
    $today = Get-Date -Format "yyyy-MM-dd"
    $logEntry = @"

### v$Version ($today) — $ReleaseNotes
"@
    $readme = Get-Content $readmeFile -Raw -Encoding UTF8
    if ($readme -match '(?m)^## 更新日志\r?\n') {
        $readme = $readme -replace '(?m)^## 更新日志\r?\n', "## 更新日志`n$logEntry`n"
    } else {
        Write-Host "README 中未找到 '## 更新日志' 标题，跳过追加" -ForegroundColor Yellow
    }
    [System.IO.File]::WriteAllText($readmeFile, $readme, [System.Text.UTF8Encoding]::new($false))
    Write-Host "README 更新日志已追加" -ForegroundColor Green

    # ── 4. 编译（AGP 9.2.1 要求 Gradle >= 9.4.1，不能用旧版 8.11.1） ──
    Write-Host "编译中..." -ForegroundColor Cyan
    Push-Location $repo
    & "D:\RuanJian\Android tool\Gradle\gradle-9.4.1\bin\gradle.bat" assembleRelease 2>&1 | Select-Object -Last 30
    $buildOk = $LASTEXITCODE -eq 0
    Pop-Location
    if (-not $buildOk) {
        Write-Host "编译失败，中止发布" -ForegroundColor Red
        exit 1
    }
    Copy-Item (Join-Path $repo "app\build\outputs\apk\release\app-release-unsigned.apk") $apkPath -Force
    Write-Host "APK 已打包: $apkPath" -ForegroundColor Green

    # ── 5. 提交 + 推送（推 HEAD 到远程 main，避免推错旧 master 分支；失败即中止） ──
    Push-Location $repo
    git add -A
    git -c user.name="JuYuMao" -c user.email="juyumao@local" commit -m "release: v$Version - $ReleaseNotes" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "git commit 失败（可能无变更），中止发布" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    git -c http.sslVerify=false push origin HEAD:main 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "git push 失败，中止发布" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    Write-Host "代码已推送" -ForegroundColor Green

    # ── 6. 创建 GitHub Release ──
    $body = @{
        tag_name = "v$Version"
        name = "v$Version - $ReleaseNotes"
        body = $ReleaseNotes
        draft = $false
        prerelease = $false
    } | ConvertTo-Json

    $pyScript = @"
import json, urllib.request, os
token = os.environ.get("GITHUB_TOKEN")
payload = json.loads(r'''$($body -replace "'", "\u0027")''')
req = urllib.request.Request(
    "https://api.github.com/repos/$github/releases",
    data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
    headers={"Authorization": f"token {token}", "Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req) as resp:
        print(json.loads(resp.read().decode())["id"])
except urllib.error.HTTPError as e:
    print("ERR:" + e.read().decode()[:300]); exit(1)
"@
    # 临时脚本放 TEMP 目录（放仓库根会被 git add -A 误提交），try/finally 保证清理
    $pyFile = Join-Path $env:TEMP "_release_tmp.py"
    try {
        [System.IO.File]::WriteAllText($pyFile, $pyScript, [System.Text.UTF8Encoding]::new($false))
        $releaseId = python $pyFile
    } finally {
        Remove-Item $pyFile -Force -ErrorAction SilentlyContinue
    }

    if ($releaseId -match '^\d+$') {
        # 上传 APK
        $upScript = @"
import json, urllib.request, os
token = os.environ.get("GITHUB_TOKEN")
url = "https://uploads.github.com/repos/$github/releases/$releaseId/assets?name=$apkName"
with open(r"$apkPath", "rb") as f: data = f.read()
req = urllib.request.Request(url, data=data, headers={
    "Authorization": f"token {token}",
    "Content-Type": "application/vnd.android.package-archive",
}, method="POST")
try:
    with urllib.request.urlopen(req) as resp:
        print(json.loads(resp.read().decode())["browser_download_url"])
except urllib.error.HTTPError as e:
    print("ERR:" + e.read().decode()[:300]); exit(1)
"@
        $upFile = Join-Path $env:TEMP "_upload_tmp.py"
        try {
            [System.IO.File]::WriteAllText($upFile, $upScript, [System.Text.UTF8Encoding]::new($false))
            $dlUrl = python $upFile
        } finally {
            Remove-Item $upFile -Force -ErrorAction SilentlyContinue
        }
        if ($dlUrl -notmatch '^https?://') {
            Write-Host "APK 上传失败: $dlUrl" -ForegroundColor Red
            exit 1
        }
        Write-Host "发布完成!" -ForegroundColor Green
        Write-Host "Release: https://github.com/$github/releases/tag/v$Version"
        Write-Host "APK 下载: $dlUrl"
    } else {
        Write-Host "Release 创建失败: $releaseId" -ForegroundColor Red
        exit 1
    }
} finally {
    # 清理环境中的 token，缩小泄漏面
    Remove-Item Env:GITHUB_TOKEN -ErrorAction SilentlyContinue
}
