[CmdletBinding()]
param(
 [string]$EnvFile='',
 [ValidateSet('validate','run')][string]$Mode='run',
 [string]$MavenCommand=''
)
$ErrorActionPreference='Stop'
if(!$EnvFile){$EnvFile=Join-Path $PSScriptRoot '..\.env'}
if(!$MavenCommand){$MavenCommand=Join-Path $PSScriptRoot '..\mvnw.cmd'}
$allowed=@('DB_URL','DB_USERNAME','DB_PASSWORD','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD','MANABI_EXPORT_MAX_BYTES','MANABI_ADMIN_USERNAME','MANABI_ADMIN_PASSWORD','MANABI_PUBLICATION_ROOT','SPRING_PROFILES_ACTIVE','MANABI_ADMIN_ALLOWED_ORIGINS','MANABI_SESSION_COOKIE_SECURE','MANABI_SESSION_SAME_SITE','MANABI_SESSION_TIMEOUT','MANABI_FORWARD_HEADERS_STRATEGY','MANABI_API_DOCS_ENABLED','MANABI_BIND_ADDRESS')
if(Test-Path -LiteralPath $EnvFile){
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile))){
  $text=$line.Trim()
  if(!$text -or $text.StartsWith('#')){continue}
  if($text -notmatch '^(?<name>[A-Z][A-Z0-9_]*)=(?<value>.*)$'){throw 'Invalid .env syntax; values omitted'}
  $name=$Matches.name
  $value=$Matches.value.Trim()
  if($name -notin $allowed -or !$seen.Add($name)){throw 'Unsupported or duplicate .env variable; values omitted'}
  if($value.StartsWith('"') -or $value.StartsWith("'")){
   if($value.Length -lt 2 -or $value[0] -ne $value[$value.Length-1]){throw 'Unbalanced .env quoting; values omitted'}
   $value=$value.Substring(1,$value.Length-2)
  }
  # Literal assignment only. No Invoke-Expression, interpolation, shell expansion or command substitution.
  if($value -and $null -eq [Environment]::GetEnvironmentVariable($name,'Process')){
   [Environment]::SetEnvironmentVariable($name,$value,'Process')
  }
 }
}
foreach($name in @('DB_URL','DB_USERNAME','DB_PASSWORD')){
 if([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name,'Process'))){throw "Missing required variable: $name"}
}
$hasName=![string]::IsNullOrEmpty($env:MANABI_ADMIN_USERNAME)
$hasPassword=![string]::IsNullOrEmpty($env:MANABI_ADMIN_PASSWORD)
if($hasName -ne $hasPassword){throw 'Bootstrap requires both administrative variables or neither; values omitted'}
Write-Output 'External configuration loaded. Values are not printed.'
if($Mode -eq 'validate'){return}
Push-Location (Join-Path $PSScriptRoot '..')
try{
 & $MavenCommand 'spring-boot:run'
 if($LASTEXITCODE -ne 0){throw 'Application startup failed; credentials omitted'}
}finally{Pop-Location}
