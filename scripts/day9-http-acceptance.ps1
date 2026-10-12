param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UsernameA = "",
    [string]$UsernameB = "",
    [System.Security.SecureString]$PasswordA,
    [System.Security.SecureString]$PasswordB
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "day9-http-acceptance.fixed.ps1"
& $scriptPath @PSBoundParameters
