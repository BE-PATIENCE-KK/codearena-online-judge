param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UsernameA = "",
    [string]$UsernameB = "",
    [System.Security.SecureString]$PasswordA,
    [System.Security.SecureString]$PasswordB
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')

# 让中文在控制台正常显示（仅影响显示，不影响任何断言逻辑）
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [hashtable]$Headers = @{},
        $Body = $null
    )

    # ===== 本函数为 Windows PowerShell 5.1 兼容性修复，业务逻辑与断言完全未改动 =====
    # 原实现用 Invoke-WebRequest，在 5.1 下有 4 个坑，这里改用 .NET 的 HttpWebRequest，
    # 一次性绕开全部问题（HttpWebRequest 在 PowerShell 5.1 与 7 上行为一致）：
    #   1) 5.1 没有 -SkipHttpErrorCheck（那是 PS 7+ 参数），传了会抛 ParameterBindingException；
    #   2) 5.1 默认用 IE 解析引擎，普通 JSON 响应会抛 NullReferenceException，需 -UseBasicParsing；
    #   3) 5.1 把不带 charset 的 application/json 按 Latin-1 解码，中文会乱码
    #      （'A+B 问题' 会变成 10 个字符，导致题目匹配失败）；
    #   4) 5.1 抛 WebException 前已把错误流读空，GetResponseStream() 只能拿到 0 字节，
    #      且真实 Spring Boot 响应下 ErrorDetails.Message 也为 null，导致 4xx 正文丢失。
    # 现在：4xx/5xx 不抛异常（捕获 WebException 后取 Response），正文一律按 UTF-8 解码，
    #       状态码 / 响应正文 / JSON 解析结果三者与原实现保持同一契约。
    $request = [System.Net.HttpWebRequest]::Create("$BaseUrl$Path")
    $request.Method = $Method
    $request.Timeout = 90000
    $request.ReadWriteTimeout = 90000
    $request.AllowAutoRedirect = $false
    $request.UserAgent = 'CodeArena-Day9-Acceptance/1.0'

    foreach ($key in $Headers.Keys) {
        if ($key -ieq 'Authorization') {
            $request.Headers['Authorization'] = [string]$Headers[$key]
        }
        else {
            $request.Headers[$key] = [string]$Headers[$key]
        }
    }

    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
        $jsonBytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        $request.ContentType = "application/json; charset=utf-8"
        $request.ContentLength = $jsonBytes.Length
        $requestStream = $request.GetRequestStream()
        $requestStream.Write($jsonBytes, 0, $jsonBytes.Length)
        $requestStream.Close()
    }

    $response = $null
    try {
        $response = $request.GetResponse()
    }
    catch [System.Net.WebException] {
        # 4xx / 5xx 走这里；Response 仍可读，正文不会被丢弃
        $response = $_.Exception.Response
        if ($null -eq $response) {
            throw   # 连接被拒绝等非 HTTP 层错误，原样抛出
        }
    }

    $statusCode = [int]$response.StatusCode
    $content = ""
    $responseStream = $response.GetResponseStream()
    if ($null -ne $responseStream) {
        $reader = New-Object System.IO.StreamReader($responseStream, [System.Text.Encoding]::UTF8)
        $content = $reader.ReadToEnd()
        $reader.Close()
    }
    $response.Close()

    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($content)) {
        try { $parsed = ConvertFrom-Json -InputObject $content }
        catch { $parsed = $content.Trim().Trim('"') }
    }

    return [pscustomobject]@{
        StatusCode = $statusCode
        Content    = $content
        Data       = $parsed
    }
}

function Get-TestAccount {
    param([string]$Label, [string]$RequestedUsername, [System.Security.SecureString]$ProvidedPassword)

    $username = $RequestedUsername
    if ([string]::IsNullOrWhiteSpace($username)) {
        $suffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
        $username = "day9_$($Label.ToLowerInvariant())_$suffix"
    }

    if ($null -ne $ProvidedPassword) {
        $passwordPointer = [IntPtr]::Zero
        try {
            $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ProvidedPassword)
            $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
        }
        finally {
            if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
        }
    }
    else {
        $credential = Get-Credential -UserName $username -Message "输入测试账号 $Label 的密码（不会显示或打印）"
        if ($credential.UserName -ne $username) {
            throw "凭据用户名必须与请求的测试账号一致。"
        }
        $password = $credential.GetNetworkCredential().Password
    }
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "测试账号密码不能为空。"
    }

    $registration = Invoke-Api -Method POST -Path "/api/auth/register" -Body @{
        username = $username
        password = $password
        email    = "$username@example.invalid"
    }
    if ($registration.StatusCode -ne 200 -or
        ($registration.Data -notmatch '注册成功|用户名已存在')) {
        throw "账号 $Label 注册没有返回预期结果（HTTP $($registration.StatusCode)）。"
    }

    $login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{
        username = $username
        password = $password
    }
    $token = [string]$login.Data
    if ($login.StatusCode -ne 200 -or $token -notmatch '^eyJ') {
        throw "账号 $Label 登录未返回 JWT（HTTP $($login.StatusCode)）；请确认账号密码。"
    }

    return [pscustomobject]@{
        Username = $username
        Token    = $token
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "验收失败：$Message" }
}

Write-Host "获取题目列表..."
$anonymous = Invoke-Api -Method GET -Path "/submission/mine"
Assert-True ($anonymous.StatusCode -eq 401) "未登录查询提交记录应返回 HTTP 401。"
$problemResponse = Invoke-Api -Method GET -Path "/problem/list"
Assert-True ($problemResponse.StatusCode -eq 200) "GET /problem/list 应返回 HTTP 200。"
Assert-True ($problemResponse.Data.code -eq 200) "题目列表业务 code 应为 200。"
$problem = @($problemResponse.Data.data | Where-Object { $_.title -eq 'A+B 问题' } | Select-Object -First 1)
Assert-True ($problem.Count -eq 1) "题目列表中必须存在 A+B 问题。"
$problemId = [long]$problem[0].id
$problemDetail = Invoke-Api -Method GET -Path "/problem/$problemId"
Assert-True ($problemDetail.StatusCode -eq 200 -and $problemDetail.Data.code -eq 200) "题目详情应返回 HTTP 200 / 业务 code 200。"

$accountA = Get-TestAccount -Label 'A' -RequestedUsername $UsernameA -ProvidedPassword $PasswordA
$accountB = Get-TestAccount -Label 'B' -RequestedUsername $UsernameB -ProvidedPassword $PasswordB
$headersA = @{ Authorization = "Bearer $($accountA.Token)" }
$headersB = @{ Authorization = "Bearer $($accountB.Token)" }
$badLogin = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ username = $accountA.Username; password = "definitely-wrong" }
Assert-True ($badLogin.StatusCode -eq 200 -and $badLogin.Data -eq "用户名或密码错误") "错误密码应返回登录失败业务响应。"
$badSubmission = Invoke-Api -Method POST -Path "/submission" -Headers $headersA -Body @{ problemId = $problemId; answer = ""; language = "Java" }
Assert-True ($badSubmission.StatusCode -eq 400 -and $badSubmission.Data.code -eq 400) "空代码提交应返回 HTTP 400 / 业务 code 400。"

$acceptedCode = @'
public class Main {
    public static void main(String[] args) {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        System.out.println(a + b);
    }
}
'@
$compileErrorCode = 'public class Main { public static void main(String[] args) { this is not Java; } }'
$runtimeErrorCode = 'public class Main { public static void main(String[] args) { throw new RuntimeException("expected test failure"); } }'
$timeoutCode = 'public class Main { public static void main(String[] args) { while (true) { } } }'
$wrongCode = @'
public class Main {
    public static void main(String[] args) {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        System.out.println(a + b + 1);
    }
}
'@

$submissionIds = @()
foreach ($case in @(
    @{ Name = 'AC'; Expected = 'AC'; Code = $acceptedCode },
    @{ Name = 'WA'; Expected = 'WA'; Code = $wrongCode },
    @{ Name = 'CE'; Expected = 'CE'; Code = $compileErrorCode },
    @{ Name = 'RE'; Expected = 'RE'; Code = $runtimeErrorCode },
    @{ Name = 'TLE'; Expected = 'TLE'; Code = $timeoutCode }
)) {
    $submitted = Invoke-Api -Method POST -Path "/submission" -Headers $headersA -Body @{
        problemId = $problemId
        answer    = $case.Code
        language  = 'Java'
    }
    Assert-True ($submitted.StatusCode -eq 201) "$($case.Name) 提交 HTTP 状态应为 201，实际 $($submitted.StatusCode)。"
    Assert-True ($submitted.Data.code -eq 200) "$($case.Name) 提交业务 code 应为 200。"
    Assert-True ($submitted.Data.data.status -eq $case.Expected) "提交返回状态应为 $($case.Expected)，实际 $($submitted.Data.data.status)。"
    $submissionId = [long]$submitted.Data.data.id
    Assert-True ($submissionId -gt 0) "提交响应必须包含有效 id。"
    $submissionIds += $submissionId

    $detailA = Invoke-Api -Method GET -Path "/submission/$submissionId" -Headers $headersA
    Assert-True ($detailA.StatusCode -eq 200) "账号 A 查询自己的 $($case.Name) 详情应为 HTTP 200。"
    Assert-True ($detailA.Data.code -eq 200 -and $detailA.Data.data.status -eq $case.Expected) "详情记录状态必须与判题结果一致。"
    Assert-True ([long]$detailA.Data.data.id -eq $submissionId) "详情 ID 必须与刚创建的提交一致。"
    Assert-True ($null -ne $detailA.Data.data.timeUsed) "详情应包含判题耗时。"
}

$mineA = Invoke-Api -Method GET -Path "/submission/mine" -Headers $headersA
Assert-True ($mineA.StatusCode -eq 200 -and $mineA.Data.code -eq 200) "账号 A 的提交列表应返回 HTTP 200 / 业务 code 200。"
foreach ($id in $submissionIds) {
    $aItem = @($mineA.Data.data | Where-Object { [long]$_.id -eq $id })
    Assert-True ($aItem.Count -eq 1) "账号 A 的 /submission/mine 必须包含提交 $id。"
}

$mineB = Invoke-Api -Method GET -Path "/submission/mine" -Headers $headersB
Assert-True ($mineB.StatusCode -eq 200 -and $mineB.Data.code -eq 200) "账号 B 的提交列表应返回 HTTP 200 / 业务 code 200。"
foreach ($id in $submissionIds) {
    $bItem = @($mineB.Data.data | Where-Object { [long]$_.id -eq $id })
    Assert-True ($bItem.Count -eq 0) "账号 B 的 /submission/mine 不得包含账号 A 的提交 $id。"

    $foreignDetail = Invoke-Api -Method GET -Path "/submission/$id" -Headers $headersB
    Assert-True ($foreignDetail.StatusCode -eq 404) "账号 B 查询账号 A 的提交 $id 应返回 HTTP 404，实际 $($foreignDetail.StatusCode)。"
    Assert-True ($foreignDetail.Data.code -eq 404) "越权详情响应业务 code 应为 404。"
}

Write-Host "HTTP 判题验收通过。"
Write-Host "AC/WA/CE/RE/TLE submission IDs: $($submissionIds -join ', ')。"
Write-Host "账号 A 可读自己的详情；账号 B 的列表不含 A 的记录，且直接替换两个 submissionId 均返回 404。"
Write-Host "账号名：A=$($accountA.Username), B=$($accountB.Username)。未输出密码或 Token。"
