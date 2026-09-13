[CmdletBinding()]
param(
    [string] $MigrationPath,
    [string] $SmokePath,
    [string] $ConcurrencyPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($MigrationPath)) {
    $MigrationPath = Join-Path (Split-Path -Parent $scriptRoot) "collaboration_groups_g3_tasks.sql"
}
if ([string]::IsNullOrWhiteSpace($SmokePath)) {
    $SmokePath = Join-Path $scriptRoot "collaboration_groups_g3_tasks_smoke.sql"
}
if ([string]::IsNullOrWhiteSpace($ConcurrencyPath)) {
    $ConcurrencyPath = Join-Path $scriptRoot "collaboration_groups_g3_lock_order_concurrency.sql"
}

function Test-Required([string] $Label, [bool] $Condition) {
    if ($Condition) {
        [Console]::WriteLine("[OK] $Label")
        return $true
    }

    [Console]::WriteLine("[FAIL] $Label")
    return $false
}

function Require-Text([string] $Label, [string] $Pattern) {
    $script:allPassed = (Test-Required $Label ([regex]::IsMatch($script:sql, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase))) -and $script:allPassed
}

function Get-FunctionMatch([string] $Name) {
    $escapedName = [regex]::Escape($Name)
    $functionPattern = "(?is)create\s+or\s+replace\s+function\s+public\.$escapedName\b.*?\`$function\`$\s*;"
    return [regex]::Match($script:sql, $functionPattern)
}

function Require-Function([string] $Name, [string] $SignaturePattern, [bool] $RequiresExpectedVersion = $false) {
    $functionMatch = Get-FunctionMatch $Name
    $found = Test-Required "RPC $Name exists" $functionMatch.Success
    $script:allPassed = $found -and $script:allPassed
    if (-not $found) {
        return
    }

    $functionBody = $functionMatch.Value.ToLowerInvariant()
    $checks = @(
        [pscustomobject]@{ Label = "RPC $Name is SECURITY DEFINER"; Passed = ($functionBody -match "security\s+definer") },
        [pscustomobject]@{ Label = "RPC $Name pins search_path"; Passed = ($functionBody -match "set\s+search_path\s*=\s*''") },
        [pscustomobject]@{ Label = "RPC $Name derives actor from auth.uid"; Passed = ($functionBody -match "auth\.uid\s*\(\s*\)") },
        [pscustomobject]@{ Label = "RPC $Name returns typed envelope"; Passed = ($functionBody -match "collaboration_mutation_envelope") },
        [pscustomobject]@{ Label = "RPC $Name locks task row"; Passed = ($functionBody -match "from\s+public\.group_tasks[\s\S]+for\s+update") },
        [pscustomobject]@{ Label = "RPC $Name maps deadlock to typed CONFLICT envelope"; Passed = ($functionBody -match "when\s+deadlock_detected[\s\S]*?collaboration_mutation_envelope\s*\(\s*'conflict'") }
    )
    if ($RequiresExpectedVersion) {
        $checks += [pscustomobject]@{ Label = "RPC $Name accepts expected version"; Passed = ($functionBody -match "expected_version\s+bigint") }
        $checks += [pscustomobject]@{ Label = "RPC $Name checks version conflict"; Passed = ($functionBody -match "conflict" -and $functionBody -match "version") }
        $checks += [pscustomobject]@{ Label = "RPC $Name increments version atomically"; Passed = ($functionBody -match "version\s*=\s*version\s*\+\s*1") }
    }
    foreach ($check in $checks) {
        $script:allPassed = (Test-Required $check.Label ([bool] $check.Passed)) -and $script:allPassed
    }
}

function Require-LockOrder([string] $Name) {
    $functionMatch = Get-FunctionMatch $Name
    if (-not $functionMatch.Success) {
        $script:allPassed = (Test-Required "RPC $Name lock order can be inspected" $false) -and $script:allPassed
        return
    }

    $functionBody = $functionMatch.Value.ToLowerInvariant()
    # Keep each match within one SQL statement so a non-locking task lookup
    # cannot be mistaken for the later FOR UPDATE task lock.
    $groupLock = [regex]::Match($functionBody, "from\s+public\.collaboration_groups[^;]*for\s+update")
    $taskLock = [regex]::Match($functionBody, "from\s+public\.group_tasks[^;]*for\s+update")
    $ordered = $groupLock.Success -and $taskLock.Success -and ($groupLock.Index -lt $taskLock.Index)
    $script:allPassed = (Test-Required "RPC $Name locks group before task" $ordered) -and $script:allPassed
}

$resolvedMigrationPath = [System.IO.Path]::GetFullPath($MigrationPath)
$migrationExists = Test-Path -LiteralPath $resolvedMigrationPath -PathType Leaf
$allPassed = Test-Required "G3 migration exists: $resolvedMigrationPath" $migrationExists
if (-not $migrationExists) {
    Write-Output "[FAIL] G3 structural checks failed"
    exit 1
}

$sql = (Get-Content -LiteralPath $resolvedMigrationPath -Raw).ToLowerInvariant()

Require-Text "migration starts a transaction" "(?m)^\s*begin\s*;"
Require-Text "migration commits a transaction" "(?m)^\s*commit\s*;\s*$"
Require-Text "task primary key remains idempotent client task ID" "p_task_id\s+uuid[\s\S]+insert\s+into\s+public\.group_tasks[\s\S]+on\s+conflict\s*\(\s*id\s*\)"
Require-Text "task title validation" "btrim\s*\(\s*p_title\s*\)\s*=\s*''"
Require-Text "reminder offset cardinality validation" "array_length\s*\([\s\S]+not between\s+1\s+and\s+5"
Require-Text "positive reminder offset validation" "offset_seconds\s*>\s*0"
Require-Text "unique reminder offset validation" "count\s*\(\s*distinct\s+offset_seconds\s*\)"
Require-Text "reminder offsets replaced transactionally" "delete\s+from\s+public\.group_task_reminders[\s\S]+insert\s+into\s+public\.group_task_reminders"
Require-Text "assignee must be current member" "group_members[\s\S]+assignee_id\s*=\s*p_assignee_id"
Require-Text "only authenticated RPC execution" "grant\s+execute\s+on\s+function\s+public\.create_group_task"
Require-Text "direct authenticated task writes revoked" "revoke\s+all\s+on\s+table\s+public\.group_tasks\s+from\s+anon,\s+authenticated"
Require-Text "direct authenticated reminder writes revoked" "revoke\s+all\s+on\s+table\s+public\.group_task_reminders\s+from\s+anon,\s+authenticated"
Require-Text "task read policy remains member-only" "create\s+policy\s+group_tasks_select_member[\s\S]+for\s+select[\s\S]+private\.is_collaboration_group_member\s*\(\s*group_id\s*\)"
Require-Text "task reminder read policy remains member-only" "create\s+policy\s+group_task_reminders_select_member[\s\S]+for\s+select[\s\S]+private\.is_collaboration_task_member\s*\(\s*task_id\s*\)"
Require-Text "group-first lock order is documented" "lock order[\s\S]+group row first[\s\S]+task row"

$taskRpcNames = @(
    "create_group_task",
    "edit_group_task",
    "reassign_group_task",
    "start_group_task",
    "complete_group_task",
    "cancel_group_task",
    "reopen_group_task"
)
$existingTaskRpcNames = @(
    "edit_group_task",
    "reassign_group_task",
    "start_group_task",
    "complete_group_task",
    "cancel_group_task",
    "reopen_group_task"
)
foreach ($rpc in $taskRpcNames) {
    Require-Function $rpc "" ($existingTaskRpcNames -contains $rpc)
    Require-LockOrder $rpc
    $rpcMatch = Get-FunctionMatch $rpc
    $rpcBody = $rpcMatch.Value.ToLowerInvariant()
    $groupTargetPattern = "'group_id'\s*,\s*(?:p_group_id|v_task\.group_id|v_task_group_id)"
    $allPassed = (Test-Required "RPC $rpc exposes group_id in task envelope data" ($rpcBody -match $groupTargetPattern)) -and $allPassed
}

Require-Text "TODO to IN_PROGRESS state transition" "status\s*(?:<>|=)\s*'todo'[\s\S]+status\s*=\s*'in_progress'"
Require-Text "TODO or IN_PROGRESS to COMPLETED transitions" "status\s+not\s+in\s*\(\s*'todo'\s*,\s*'in_progress'\s*\)[\s\S]+status\s*=\s*'completed'"
Require-Text "TODO or IN_PROGRESS to CANCELLED transitions" "status\s+not\s+in\s*\(\s*'todo'\s*,\s*'in_progress'\s*\)[\s\S]+status\s*=\s*'cancelled'"
Require-Text "terminal task reopen transition" "status\s+not\s+in\s*\(\s*'completed'\s*,\s*'cancelled'\s*\)[\s\S]+status\s*=\s*'todo'"
Require-Text "assignee-only completion authorization" "v_actor\s*<>\s*v_task\.assignee_id[\s\S]+not_authorized"
Require-Text "creator owner admin cancellation authorization" "v_actor\s*<>\s*v_task\.created_by[\s\S]+v_actor_role\s+not\s+in\s*\(\s*'owner'\s*,\s*'admin'"
Require-Text "conflict envelope status" "'conflict'"
Require-Text "not authorized envelope status" "'not_authorized'"
Require-Text "not found envelope status" "'not_found'"
Require-Text "invalid state envelope status" "'invalid_state'"
Require-Text "validation envelope status" "'validation'"

$smokeExists = Test-Path -LiteralPath ([System.IO.Path]::GetFullPath($SmokePath)) -PathType Leaf
$allPassed = (Test-Required "opt-in two-account smoke script exists" $smokeExists) -and $allPassed
if ($smokeExists) {
    $smokeSql = (Get-Content -LiteralPath ([System.IO.Path]::GetFullPath($SmokePath)) -Raw).ToLowerInvariant()
    $dollarBlockPattern = '(?is)(?<delimiter>\$(?:[a-z_][a-z0-9_]*)?\$)(?<body>.*?)\k<delimiter>'
    $dollarBlocks = [regex]::Matches($smokeSql, $dollarBlockPattern)
    $variableTokensInsideDollarBlocks = @(
        $dollarBlocks | Where-Object {
            $_.Groups["body"].Value -match ":'[_a-z][_a-z0-9]*'"
        }
    )
    $smokeChecks = @(
        [pscustomobject]@{ Label = "smoke script is opt-in and requires explicit target"; Passed = ($smokeSql -match "development" -and $smokeSql -match "do not run against production") },
        [pscustomobject]@{ Label = "smoke script uses two actors"; Passed = ($smokeSql -match "user_a" -and $smokeSql -match "user_b") },
        [pscustomobject]@{ Label = "smoke script switches JWT actor"; Passed = ($smokeSql -match "request\.jwt\.claim\.sub") },
        [pscustomobject]@{ Label = "smoke script checks idempotent create"; Passed = ($smokeSql -match "create_group_task" -and $smokeSql -match "idempot") },
        [pscustomobject]@{ Label = "smoke script checks conflict"; Passed = ($smokeSql -match "expected_version" -and $smokeSql -match "conflict") },
        [pscustomobject]@{ Label = "smoke script has no psql variables inside dollar blocks"; Passed = ($variableTokensInsideDollarBlocks.Count -eq 0) },
        [pscustomobject]@{ Label = "smoke script covers invalid state"; Passed = ($smokeSql -match "invalid_state") },
        [pscustomobject]@{ Label = "smoke script covers outsider or removed-member reads"; Passed = ($smokeSql -match "outsider" -or $smokeSql -match "removed") },
        [pscustomobject]@{ Label = "smoke script rolls back data"; Passed = ($smokeSql -match "rollback\s*;") }
    )
    foreach ($check in $smokeChecks) {
        $allPassed = (Test-Required $check.Label ([bool] $check.Passed)) -and $allPassed
    }
}

$resolvedConcurrencyPath = [System.IO.Path]::GetFullPath($ConcurrencyPath)
$concurrencyExists = Test-Path -LiteralPath $resolvedConcurrencyPath -PathType Leaf
$allPassed = (Test-Required "concurrent lock-order smoke script exists" $concurrencyExists) -and $allPassed
if ($concurrencyExists) {
    $concurrencySql = (Get-Content -LiteralPath $resolvedConcurrencyPath -Raw).ToLowerInvariant()
    $concurrencyChecks = @(
        [pscustomobject]@{ Label = "concurrent smoke requires development target"; Passed = ($concurrencySql -match "development" -and $concurrencySql -match "production") },
        [pscustomobject]@{ Label = "concurrent smoke exercises create retry"; Passed = ($concurrencySql -match "create_group_task" -and $concurrencySql -match "retry") },
        [pscustomobject]@{ Label = "concurrent smoke exercises existing-task mutation"; Passed = ($concurrencySql -match "start_group_task" -or $concurrencySql -match "cancel_group_task") },
        [pscustomobject]@{ Label = "concurrent smoke documents two sessions"; Passed = ($concurrencySql -match "session\s+a" -and $concurrencySql -match "session\s+b") },
        [pscustomobject]@{ Label = "concurrent smoke keeps assertions outside dollar blocks"; Passed = (-not [regex]::IsMatch($concurrencySql, "\$[a-z_]*\$[\s\S]*:'[_a-z][_a-z0-9]*'[\s\S]*\$[a-z_]*\$", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) }
    )
    foreach ($check in $concurrencyChecks) {
        $allPassed = (Test-Required $check.Label ([bool] $check.Passed)) -and $allPassed
    }
}

if ($allPassed) {
    Write-Output "[OK] G3 structural checks passed"
    exit 0
}

Write-Output "[FAIL] G3 structural checks failed"
exit 1
