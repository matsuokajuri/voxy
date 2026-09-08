param(
    [string]$BaselineRef = 'HEAD',
    [int]$Forks = 5,
    [int]$Warmup = 12,
    [int]$Samples = 32,
    [int]$MinWarmupMillis = 2000,
    [string]$Scenario = 'all',
    [string]$OutputDirectory = '',
    [string]$JavaHome = '',
    [string]$RuntimeFastutilJar = '',
    [switch]$PrintCompilation,
    [switch]$CompileOnly
)

$ErrorActionPreference = 'Stop'
function Get-Sha256([string]$path) {
    $stream = [IO.File]::OpenRead($path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$javaExecutable = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$javacExecutable = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$classpathFile = Join-Path $repo 'build/tmp/round11-benchmark-classpath.txt'
if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw 'Export test runtime classpath with tools/benchmarks/round11-classpath.init.gradle first (coordinate Gradle slot).'
}
if ($Forks -lt 1 -or $Samples -lt 4 -or $Warmup -lt 1) { throw 'Invalid benchmark sampling bounds.' }
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repo ('build/reports/round11-cpu-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$output = [IO.Path]::GetFullPath($OutputDirectory)
$allowed = [IO.Path]::GetFullPath((Join-Path $repo 'build')) + [IO.Path]::DirectorySeparatorChar
if (-not $output.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Benchmark output must remain inside this repository build directory.'
}
if (Test-Path -LiteralPath $output) { throw 'Use a fresh output directory; benchmark records are immutable.' }
[IO.Directory]::CreateDirectory($output) | Out-Null
$classpath = [IO.File]::ReadAllText($classpathFile).Trim()
# A concurrent Gradle build must not replace a class file while isolated javac/java reads it.
# Freeze directory entries from build; dependency JARs under Gradle's immutable caches stay shared.
$classpathEntries = @($classpath.Split([IO.Path]::PathSeparator))
for ($index = 0; $index -lt $classpathEntries.Count; $index++) {
    $entry = $classpathEntries[$index]
    if ((Test-Path -LiteralPath $entry -PathType Container) -and
            [IO.Path]::GetFullPath($entry).StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
        $frozen = Join-Path $output ("dependency-directory-$index")
        Copy-Item -LiteralPath $entry -Destination $frozen -Recurse
        $classpathEntries[$index] = $frozen
    }
}
$classpath = $classpathEntries -join [IO.Path]::PathSeparator
$runtimeAbiFile = Join-Path $repo 'build/tmp/round11-benchmark-fastutil-runtime.txt'
if (-not $RuntimeFastutilJar) {
    if (-not (Test-Path -LiteralPath $runtimeAbiFile)) { throw 'Export the runtime ABI artifact or pass -RuntimeFastutilJar (actual fastutil 8.5.9).' }
    $RuntimeFastutilJar = [IO.File]::ReadAllText($runtimeAbiFile).Trim()
}
if (-not (Test-Path -LiteralPath $RuntimeFastutilJar -PathType Leaf) -or [IO.Path]::GetFileName($RuntimeFastutilJar) -ne 'fastutil-8.5.9.jar') {
    throw 'The final CPU comparison must use the actual Minecraft fastutil-8.5.9.jar.'
}
$runtimeEntries = @($classpathEntries | Where-Object { [IO.Path]::GetFileName($_) -notmatch '^fastutil-[0-9.]+\.jar$' })
$runtimeClasspathBase = (@($RuntimeFastutilJar) + $runtimeEntries) -join [IO.Path]::PathSeparator
$baseline = (& git -C $repo rev-parse $BaselineRef).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Baseline ref did not resolve.' }
$paths = @(
    'src/main/java/me/cortex/voxy/common/util/AllocationArena.java',
    'src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java',
    'src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java',
    'src/main/java/me/cortex/voxy/forge/BuiltSection.java',
    'src/main/java/me/cortex/voxy/forge/GeometryCache.java',
    'src/main/java/me/cortex/voxy/forge/ISectionWatcher.java',
    'src/main/java/me/cortex/voxy/forge/NodeManager.java',
    'src/main/java/me/cortex/voxy/forge/NodeStore.java',
    'src/main/java/me/cortex/voxy/forge/RenderGenerationService.java',
    'src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java'
)
# Freeze both exact source sets without switching, resetting or modifying the user's worktree.
$archive = Join-Path $output 'baseline-sources.zip'
& git -C $repo archive --format=zip "--output=$archive" $baseline -- @paths
if ($LASTEXITCODE -ne 0) { throw 'Unable to archive baseline sources.' }
$baselineRoot = Join-Path $output 'baseline-src'
Expand-Archive -LiteralPath $archive -DestinationPath $baselineRoot
$candidateRoot = Join-Path $output 'candidate-src'
$hashes = @()
foreach ($relative in $paths) {
    $source = Join-Path $repo $relative
    $target = Join-Path $candidateRoot $relative
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
    $hashes += [ordered]@{ path=$relative; sha256=(Get-Sha256 $target) }
}
$harness = Join-Path $output 'Round11CpuBenchmark.java'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Round11CpuBenchmark.java') -Destination $harness
$classDirectories = @{}
foreach ($variant in @('baseline','candidate')) {
    $classes = Join-Path $output ($variant + '-classes')
    [IO.Directory]::CreateDirectory($classes) | Out-Null
    $classDirectories[$variant] = $classes
    $sourceRoot = if ($variant -eq 'baseline') { $baselineRoot } else { $candidateRoot }
    $arguments = @('--release', '17', '-encoding', 'UTF-8', '-proc:none', '-implicit:none',
        '-classpath', $classpath, '-d', $classes)
    $arguments += $paths | ForEach-Object { Join-Path $sourceRoot $_ }
    $arguments += $harness
    # javac response files avoid the Windows command-line size limit with the exact Gradle classpath.
    $response = Join-Path $output ($variant + '-javac.args')
    $quoted = $arguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }
    [IO.File]::WriteAllLines($response, $quoted, [Text.UTF8Encoding]::new($false))
    $ErrorActionPreference = 'Continue'
    $compileLines = @(& $javacExecutable '-J-Duser.language=en' "@$response" 2>&1)
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $compileLines | Out-File -LiteralPath (Join-Path $output ($variant + '-compile.log')) -Encoding utf8
    if ($exitCode -ne 0) { $compileLines | Write-Output; throw "$variant isolated javac failed." }
}
$environment = [ordered]@{
    baselineCommit = $baseline
    createdUtc = [DateTime]::UtcNow.ToString('o')
    java = ((& $javaExecutable --version) -join "`n")
    os = [Environment]::OSVersion.VersionString
    processor = [Environment]::GetEnvironmentVariable('PROCESSOR_IDENTIFIER')
    logicalProcessors = [Environment]::ProcessorCount
    cpuOnly = $true
    readinessEvidence = $false
    forks = $Forks
    warmupBatches = $Warmup
    measuredBatches = $Samples
    minWarmupMillis = $MinWarmupMillis
    scenario = $Scenario
    printCompilation = [bool]$PrintCompilation
    candidateSources = $hashes
    harnessSha256 = (Get-Sha256 $harness)
    classpathSha256 = (Get-Sha256 $classpathFile)
    runtimeFastutilJar = $RuntimeFastutilJar
    runtimeFastutilSha256 = (Get-Sha256 $RuntimeFastutilJar)
    jvmFlags = @('-Xms512m','-Xmx512m','-XX:ActiveProcessorCount=2','-Dvoxy.ensureTrackedObjectsAreFreed=false')
}
[IO.File]::WriteAllText((Join-Path $output 'environment.json'), ($environment | ConvertTo-Json -Depth 8),
    [Text.UTF8Encoding]::new($false))
if ($CompileOnly) { Write-Output "Compiled isolated variants: $output"; exit 0 }

$results = @()
$scenarios = if ($Scenario -eq 'all') {
    @('arena_churn','geometry_same_size','geometry_resize','geometry_publish','cache_churn',
        'node_split_remove','sync_copy_replace','async_worker_cached_split')
} else { @($Scenario) }
for ($fork = 0; $fork -lt $Forks; $fork++) {
    # Alternate order between fresh JVM pairs to reduce thermal/JIT/system-load ordering bias.
    $order = if (($fork % 2) -eq 0) { @('baseline','candidate') } else { @('candidate','baseline') }
    foreach ($scene in $scenarios) {
      foreach ($variant in $order) {
        # Each scenario gets a fresh JVM too; no cross-scenario type-profile/deoptimization pollution.
        $runtimeClasspath = $classDirectories[$variant] + [IO.Path]::PathSeparator + $runtimeClasspathBase
        $javaArguments = @($environment.jvmFlags) + @("-Dround11.bench.warmup=$Warmup",
            "-Dround11.bench.minWarmupMillis=$MinWarmupMillis",
            "-Dround11.bench.samples=$Samples", '-classpath', $runtimeClasspath,
            'me.cortex.voxy.forge.Round11CpuBenchmark', $variant, $scene)
        if ($PrintCompilation) { $javaArguments = @('-XX:+PrintCompilation') + $javaArguments }
        $response = Join-Path $output ("$variant-$scene-$fork-java.args")
        $quoted = $javaArguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }
        [IO.File]::WriteAllLines($response, $quoted, [Text.UTF8Encoding]::new($false))
        $ErrorActionPreference = 'Continue'
        $lines = @(& $javaExecutable "@$response" 2>&1)
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        $lines | Out-File -LiteralPath (Join-Path $output ("$variant-$scene-$fork.log")) -Encoding utf8
        $lines | Where-Object { $_ -match '^ROUND11_CPU ' } | Write-Output
        if ($exitCode -ne 0) { throw "$variant scene $scene fork $fork failed; retain failure evidence in $output" }
        foreach ($line in $lines) {
            if ($line -match '^ROUND11_CPU (\{.*\})$') {
                $result = $Matches[1] | ConvertFrom-Json
                $result | Add-Member -NotePropertyName fork -NotePropertyValue $fork
                $results += $result
            }
        }
      }
    }
}
$expectedResults = $Forks * 2 * $(if ($Scenario -eq 'all') { 8 } else { 1 })
if ($results.Count -ne $expectedResults) {
    throw "Expected $expectedResults complete benchmark records, got $($results.Count). Diagnostic/noisy output is not accepted as measurement."
}
[IO.File]::WriteAllText((Join-Path $output 'results.json'), ($results | ConvertTo-Json -Depth 5),
    [Text.UTF8Encoding]::new($false))
function Median($values) {
    $sorted = @($values | Sort-Object)
    if (($sorted.Count % 2) -eq 0) { return ($sorted[$sorted.Count/2-1] + $sorted[$sorted.Count/2])/2 }
    return $sorted[[int][Math]::Floor($sorted.Count/2)]
}
$summary = foreach ($name in @($results.scenario | Sort-Object -Unique)) {
    $before = @($results | Where-Object { $_.scenario -eq $name -and $_.label -eq 'baseline' })
    $after = @($results | Where-Object { $_.scenario -eq $name -and $_.label -eq 'candidate' })
    $baselineP50 = Median $before.p50_ns_per_op
    $candidateP50 = Median $after.p50_ns_per_op
    [ordered]@{
        scenario = $name
        baselineP50Ns = $baselineP50
        candidateP50Ns = $candidateP50
        p50Ratio = $candidateP50/$baselineP50
        baselineP95Ns = (Median $before.p95_ns_per_op)
        candidateP95Ns = (Median $after.p95_ns_per_op)
        baselineHeapBytesPerOp = (Median $before.p50_heap_bytes_per_op)
        candidateHeapBytesPerOp = (Median $after.p50_heap_bytes_per_op)
        baselinePeakLiveGeometryBytes = ($before.peak_live_geometry_bytes | Measure-Object -Maximum).Maximum
        candidatePeakLiveGeometryBytes = ($after.peak_live_geometry_bytes | Measure-Object -Maximum).Maximum
    }
}
[IO.File]::WriteAllText((Join-Path $output 'summary.json'), ($summary | ConvertTo-Json -Depth 5),
    [Text.UTF8Encoding]::new($false))
$summary | ForEach-Object { [pscustomobject]$_ } | Format-Table -AutoSize
Write-Output "Round 11 CPU-only benchmark results: $output"
