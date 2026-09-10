[CmdletBinding()]
param(
    [string] $MigrationPath = (Join-Path (Split-Path -Parent $PSScriptRoot) "collaboration_groups_g2_membership.sql")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-Required([string] $Label, [bool] $Condition) {
    if ($Condition) {
        [Console]::WriteLine("[OK] $Label")
    }
    else {
        [Console]::WriteLine("[FAIL] $Label")
        return $false
    }

    return $true
}

$resolvedMigrationPath = [System.IO.Path]::GetFullPath($MigrationPath)
if (-not (Test-Path -LiteralPath $resolvedMigrationPath -PathType Leaf)) {
    Write-Output "[FAIL] migration exists: $resolvedMigrationPath"
    exit 1
}

$sql = Get-Content -LiteralPath $resolvedMigrationPath -Raw
$sqlLower = $sql.ToLowerInvariant()
$allPassed = $true

function Require-Text([string] $Label, [string] $Pattern) {
    $script:allPassed = (Test-Required $Label ([regex]::IsMatch($script:sqlLower, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase))) -and $script:allPassed
}

Require-Text "migration starts a transaction" "(?m)^\s*begin\s*;"
Require-Text "migration commits a transaction" "(?m)^\s*commit\s*;\s*$"
Require-Text "user_profiles table" "create\s+table\s+if\s+not\s+exists\s+public\.user_profiles"
Require-Text "user_profiles user_id column" "user_id\s+uuid\s+primary\s+key"
Require-Text "user_profiles display_name column" "display_name\s+text"
Require-Text "user_profiles avatar_url column" "avatar_url\s+text"
Require-Text "user_profiles updated_at column" "updated_at\s+timestamptz"
Require-Text "auth user backfill" "insert\s+into\s+public\.user_profiles[\s\S]+from\s+auth\.users"
Require-Text "new account profile trigger" "create\s+trigger\s+[^;]*on\s+auth\.users"
Require-Text "profile select RLS" "create\s+policy\s+user_profiles_select_self_or_active_group[\s\S]+on\s+public\.user_profiles[\s\S]+for\s+select[\s\S]+to\s+authenticated[\s\S]+private\.is_same_active_group_profile"
Require-Text "profile update RLS" "create\s+policy\s+user_profiles_update_self[\s\S]+on\s+public\.user_profiles[\s\S]+for\s+update[\s\S]+using\s*\(user_id\s*=\s*\(select\s+auth\.uid\(\)\)\)[\s\S]+with\s+check\s*\(user_id\s*=\s*\(select\s+auth\.uid\(\)\)\)"
Require-Text "same active group profile predicate" "is_same_active_group_profile"

$collaborationTables = @(
    "collaboration_groups",
    "group_members",
    "group_invites",
    "group_tasks",
    "group_task_reminders",
    "group_reminders"
)
$collaborationPolicyPatterns = @{
    collaboration_groups = "create\s+policy\s+collaboration_groups_select_member[\s\S]+on\s+public\.collaboration_groups[\s\S]+for\s+select[\s\S]+deleted_at\s+is\s+null"
    group_members = "create\s+policy\s+group_members_select_member[\s\S]+on\s+public\.group_members[\s\S]+for\s+select[\s\S]+private\.is_active_collaboration_group\(group_id\)"
    group_invites = "create\s+policy\s+group_invites_select_recipient_or_manager[\s\S]+on\s+public\.group_invites[\s\S]+for\s+select[\s\S]+private\.is_active_collaboration_group\(group_id\)"
    group_tasks = "create\s+policy\s+group_tasks_select_member[\s\S]+on\s+public\.group_tasks[\s\S]+for\s+select[\s\S]+private\.is_active_collaboration_group\(group_id\)"
    group_task_reminders = "create\s+policy\s+group_task_reminders_select_member[\s\S]+on\s+public\.group_task_reminders[\s\S]+for\s+select[\s\S]+private\.is_collaboration_task_member\(task_id\)"
    group_reminders = "create\s+policy\s+group_reminders_select_member[\s\S]+on\s+public\.group_reminders[\s\S]+for\s+select[\s\S]+private\.is_active_collaboration_group\(group_id\)"
}
foreach ($table in $collaborationTables) {
    Require-Text "RLS enabled on $table" "alter\s+table\s+public\.$table\s+enable\s+row\s+level\s+security"
    Require-Text "public write revoke on $table" "revoke\s+all\s+on\s+table\s+public\.$table\s+from\s+public\s*;"
    Require-Text "authenticated write revoke on $table" "revoke\s+all\s+on\s+table\s+public\.$table\s+from\s+anon,\s+authenticated\s*;"
    Require-Text "specific active read policy for $table" $collaborationPolicyPatterns[$table]
}

Require-Text "active group member helper" "is_collaboration_group_member[\s\S]+collaboration_groups[\s\S]+deleted_at\s+is\s+null"
Require-Text "active group manager helper" "is_collaboration_group_manager[\s\S]+collaboration_groups[\s\S]+deleted_at\s+is\s+null"
Require-Text "active group helper" "is_active_collaboration_group[\s\S]+collaboration_groups[\s\S]+deleted_at\s+is\s+null"
Require-Text "active task member helper" "is_collaboration_task_member[\s\S]+collaboration_groups[\s\S]+deleted_at\s+is\s+null"
Require-Text "invite read excludes deleted group" "group_invites[\s\S]+collaboration_groups[\s\S]+deleted_at\s+is\s+null"
Require-Text "mutation envelope helper" "collaboration_mutation_envelope"
Require-Text "APPLIED envelope status" "applied"
Require-Text "NOT_AUTHORIZED envelope status" "not_authorized"
Require-Text "MEMBER_NOT_FOUND envelope status" "member_not_found"
Require-Text "ALREADY_MEMBER envelope status" "already_member"
Require-Text "INVITE_ALREADY_PENDING envelope status" "invite_already_pending"
Require-Text "INVALID_STATE envelope status" "invalid_state"
Require-Text "one-owner uniqueness retained" "group_members_one_owner_per_group_idx"
Require-Text "sole owner leave requires explicit delete or transfer" "owner must transfer ownership or delete group before leaving"

$rpcNames = @(
    "create_collaboration_group",
    "update_collaboration_group",
    "invite_group_member",
    "respond_group_invite",
    "change_group_member_role",
    "remove_group_member",
    "transfer_group_ownership",
    "leave_collaboration_group",
    "delete_collaboration_group"
)

$lockedRpcNames = @(
    "update_collaboration_group",
    "invite_group_member",
    "respond_group_invite",
    "change_group_member_role",
    "remove_group_member",
    "transfer_group_ownership",
    "leave_collaboration_group",
    "delete_collaboration_group"
)

foreach ($rpc in $rpcNames) {
    $escapedRpc = [regex]::Escape($rpc)
    $functionMatch = [regex]::Match(
        $sql,
        "(?is)create\s+or\s+replace\s+function\s+public\.$escapedRpc\b.*?\`$function\`$\s*;"
    )
    $found = $functionMatch.Success
    $allPassed = (Test-Required "RPC $rpc exists" $found) -and $allPassed
    if ($found) {
        $functionBody = $functionMatch.Value.ToLowerInvariant()
        $allPassed = (Test-Required "RPC $rpc is SECURITY DEFINER" ($functionBody -match "security\s+definer")) -and $allPassed
        $allPassed = (Test-Required "RPC $rpc pins search_path" ($functionBody -match "set\s+search_path\s*=\s*''")) -and $allPassed
        $allPassed = (Test-Required "RPC $rpc derives actor from auth.uid" ($functionBody -match "auth\.uid\s*\(\s*\)")) -and $allPassed
        $allPassed = (Test-Required "RPC $rpc returns typed envelope" ($functionBody -match "collaboration_mutation_envelope")) -and $allPassed
    }
}

foreach ($rpc in $lockedRpcNames) {
    $escapedRpc = [regex]::Escape($rpc)
    $functionMatch = [regex]::Match(
        $sql,
        "(?is)create\s+or\s+replace\s+function\s+public\.$escapedRpc\b.*?\`$function\`$\s*;"
    )
    $hasLock = $functionMatch.Success -and ($functionMatch.Value -match "for\s+update")
    $allPassed = (Test-Required "RPC $rpc locks membership or aggregate rows" $hasLock) -and $allPassed
}

$smokePath = Join-Path (Split-Path -Parent $resolvedMigrationPath) "tests\collaboration_groups_g2_rls_smoke.sql"
$smokeExists = Test-Path -LiteralPath $smokePath -PathType Leaf
$allPassed = (Test-Required "repeatable RLS smoke script exists" $smokeExists) -and $allPassed
if ($smokeExists) {
    $smokeSql = Get-Content -LiteralPath $smokePath -Raw
    $smokeLower = $smokeSql.ToLowerInvariant()
    $acceptIndex = $smokeLower.IndexOf("as accept_status")
    $memberAuthorizationIndex = $smokeLower.IndexOf("as self_role_status")
    $memberAuthorizationAfterAccept = $acceptIndex -ge 0 -and $memberAuthorizationIndex -gt $acceptIndex
    $allPassed = (Test-Required "member authorization checks run after invite acceptance" $memberAuthorizationAfterAccept) -and $allPassed

    $writeTableArrayMatch = [regex]::Match(
        $smokeLower,
        "(?is)v_tables\s+text\[\]\s*:=\s*array\s*\[(?<items>.*?)\]\s*;"
    )
    $hasWriteTableArray = $writeTableArrayMatch.Success
    $allPassed = (Test-Required "smoke script enumerates direct-write tables" $hasWriteTableArray) -and $allPassed
    if ($hasWriteTableArray) {
        $writeTableItems = $writeTableArrayMatch.Groups["items"].Value
        foreach ($table in $collaborationTables) {
            $tablePattern = "'" + [regex]::Escape($table) + "'"
            $allPassed = (Test-Required "smoke direct-write denial names $table" ($writeTableItems -match $tablePattern)) -and $allPassed
        }
    }

    $deletedReadPatterns = @{
        collaboration_groups = "select\s+count\(\*\)\s+as\s+deleted_group_rows[\s\S]+from\s+public\.collaboration_groups"
        group_members = "select\s+count\(\*\)\s+as\s+deleted_member_rows[\s\S]+from\s+public\.group_members"
        group_invites = "select\s+count\(\*\)\s+as\s+deleted_invite_rows[\s\S]+from\s+public\.group_invites"
        group_tasks = "select\s+count\(\*\)\s+as\s+deleted_task_rows[\s\S]+from\s+public\.group_tasks"
        group_task_reminders = "select\s+count\(\*\)\s+as\s+deleted_task_reminder_rows[\s\S]+from\s+public\.group_task_reminders"
        group_reminders = "select\s+count\(\*\)\s+as\s+deleted_reminder_rows[\s\S]+from\s+public\.group_reminders"
        user_profiles = "select\s+count\(\*\)\s+as\s+deleted_profile_rows_as_b[\s\S]+from\s+public\.user_profiles"
    }
    $deleteIndex = $smokeLower.IndexOf("as delete_status")
    $allPassed = (Test-Required "smoke script has a soft-delete checkpoint" ($deleteIndex -ge 0)) -and $allPassed
    foreach ($readTable in $deletedReadPatterns.Keys) {
        $readMatch = [regex]::Match($smokeLower, $deletedReadPatterns[$readTable])
        $readAfterDelete = $readMatch.Success -and $readMatch.Index -gt $deleteIndex
        $allPassed = (Test-Required "smoke soft-delete read assertion names $readTable" $readAfterDelete) -and $allPassed
    }

    $smokeChecks = @(
        [pscustomobject]@{ Label = "smoke script stops on SQL errors"; Passed = ($smokeLower -match "\\set\s+on_error_stop\s+on") },
        [pscustomobject]@{ Label = "smoke script uses authenticated role"; Passed = ($smokeLower -match "set\s+role\s+authenticated") },
        [pscustomobject]@{ Label = "smoke script switches JWT actor"; Passed = ($smokeLower -match "request\.jwt\.claim\.sub") },
        [pscustomobject]@{ Label = "smoke script covers both actors"; Passed = (($smokeLower -match "user_a") -and ($smokeLower -match "user_b")) },
        [pscustomobject]@{ Label = "smoke script checks direct write denial"; Passed = (($smokeLower -match "has_table_privilege") -and ($smokeLower -match "execute\s+format\('insert\s+into\s+public\.%i") -and ($smokeLower -match "insufficient_privilege")) },
        [pscustomobject]@{ Label = "smoke script rolls back data"; Passed = ($smokeLower -match "rollback\s*;") },
        [pscustomobject]@{ Label = "smoke script covers ownership transfer"; Passed = ($smokeLower -match "transfer_group_ownership") },
        [pscustomobject]@{ Label = "smoke script rejects sole owner leave"; Passed = ($smokeLower -match "sole_owner_leave_status[\s\S]+invalid_state") }
    )
    foreach ($check in $smokeChecks) {
        $allPassed = (Test-Required $check.Label ([bool] $check.Passed)) -and $allPassed
    }
}

if ($allPassed) {
    Write-Output "[OK] G2 structural checks passed"
    exit 0
}

Write-Output "[FAIL] G2 structural checks failed"
exit 1
