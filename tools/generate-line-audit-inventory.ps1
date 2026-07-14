param()

$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildFile = Join-Path $root 'build.gradle'
$buildLines = [System.IO.File]::ReadAllLines($buildFile)

$sourceSetsStart = -1
$resourcesStart = -1
$sourceSetsEnd = -1
for ($index = 0; $index -lt $buildLines.Length; $index++) {
    if ($sourceSetsStart -lt 0 -and $buildLines[$index] -match '^sourceSets\s*\{') {
        $sourceSetsStart = $index
        continue
    }
    if ($sourceSetsStart -ge 0 -and $resourcesStart -lt 0 -and $buildLines[$index] -match '^\s*resources\s*\{') {
        $resourcesStart = $index
        continue
    }
    if ($resourcesStart -ge 0 -and $buildLines[$index] -match '^\}') {
        $sourceSetsEnd = $index
        break
    }
}

if ($sourceSetsStart -lt 0 -or $resourcesStart -lt 0 -or $sourceSetsEnd -lt 0) {
    throw 'Could not locate the main sourceSets java/resources boundaries in build.gradle.'
}

function Get-IncludePatterns([int] $fromInclusive, [int] $toExclusive) {
    $patterns = [System.Collections.Generic.List[string]]::new()
    for ($index = $fromInclusive; $index -lt $toExclusive; $index++) {
        if ($buildLines[$index] -match "include\s+'([^']+)'") {
            $patterns.Add($Matches[1])
        }
    }
    return $patterns.ToArray()
}

$mainJavaPatterns = Get-IncludePatterns ($sourceSetsStart + 1) $resourcesStart
$mainResourcePatterns = Get-IncludePatterns ($resourcesStart + 1) $sourceSetsEnd

function Get-RelativeFiles([string] $relativeRoot) {
    $absoluteRoot = Join-Path $root $relativeRoot
    if (-not (Test-Path -LiteralPath $absoluteRoot)) {
        return @()
    }
    $absolutePrefix = $absoluteRoot.TrimEnd('\') + '\'
    return [System.IO.Directory]::EnumerateFiles(
            $absoluteRoot,
            '*',
            [System.IO.SearchOption]::AllDirectories) | ForEach-Object {
        $_.Substring($absolutePrefix.Length).Replace('\', '/')
    }
}

function Test-Include([string] $relativePath, [string[]] $patterns) {
    foreach ($pattern in $patterns) {
        $wildcard = $pattern.Replace('**', '*')
        if ($relativePath -like $wildcard) {
            return $true
        }
    }
    return $false
}

$paths = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)

foreach ($relativePath in Get-RelativeFiles 'src/main/java') {
    if (Test-Include $relativePath $mainJavaPatterns) {
        [void] $paths.Add("src/main/java/$relativePath")
    }
}
foreach ($relativePath in Get-RelativeFiles 'src/main/resources') {
    if (Test-Include $relativePath $mainResourcePatterns) {
        [void] $paths.Add("src/main/resources/$relativePath")
    }
}
foreach ($sourceRoot in @('src/test/java', 'src/test/resources')) {
    foreach ($relativePath in Get-RelativeFiles $sourceRoot) {
        [void] $paths.Add("$sourceRoot/$relativePath")
    }
}
foreach ($relativePath in @(
        'build.gradle',
        'settings.gradle',
        'gradle.properties',
        'gradle/wrapper/gradle-wrapper.properties',
        'tools/generate-line-audit-inventory.ps1')) {
    if (Test-Path -LiteralPath (Join-Path $root $relativePath)) {
        [void] $paths.Add($relativePath)
    }
}

function Get-AuditKind([string] $relativePath) {
    if ($relativePath.StartsWith('src/main/java/')) { return 'main-java' }
    if ($relativePath.StartsWith('src/test/java/')) { return 'test-java' }
    if ($relativePath -match '\.(vert|frag|comp|glsl)$') { return 'shader' }
    if ($relativePath.EndsWith('.png')) { return 'binary-resource' }
    if ($relativePath.EndsWith('.gradle')) { return 'build-logic' }
    if ($relativePath.EndsWith('.ps1')) { return 'audit-tool' }
    if ($relativePath.StartsWith('src/main/resources/')) { return 'main-resource' }
    if ($relativePath.StartsWith('src/test/resources/')) { return 'test-resource' }
    return 'build-config'
}

function Get-Sha256([string] $absolutePath) {
    $stream = [System.IO.File]::OpenRead($absolutePath)
    try {
        $hasher = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $hasher.ComputeHash($stream)
            return [System.BitConverter]::ToString($hash).Replace('-', '').ToLowerInvariant()
        } finally {
            $hasher.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

$inventory = foreach ($relativePath in $paths | Sort-Object) {
    $absolutePath = Join-Path $root $relativePath
    $kind = Get-AuditKind $relativePath
    $fileInfo = Get-Item -LiteralPath $absolutePath
    $lineCount = if ($kind -eq 'binary-resource') {
        $null
    } else {
        [System.IO.File]::ReadAllLines($absolutePath).Length
    }
    [ordered] @{
        path = $relativePath.Replace('\', '/')
        kind = $kind
        lines = $lineCount
        bytes = $fileInfo.Length
        sha256 = Get-Sha256 $absolutePath
    }
}

$inventory | ConvertTo-Json -Depth 3 -Compress
