<#
Minimal Minecraft RCON client for local smoke tests.

Usage:
  ./scripts/rcon.ps1 -Password <pw> -Command "save-all flush"
  ./scripts/rcon.ps1 -Password <pw> -Command "stop"
#>
param(
    [string]$Server = "127.0.0.1",
    [int]$Port = 25575,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$Command,
    [int]$TimeoutMs = 10000
)

$ErrorActionPreference = "Stop"
$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Server, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = $TimeoutMs

function Write-Packet([int]$id, [int]$type, [string]$body) {
    $payload = [System.Text.Encoding]::UTF8.GetBytes($body)
    $length = 4 + 4 + $payload.Length + 2
    $buffer = New-Object byte[] (4 + $length)
    [BitConverter]::GetBytes([int]$length).CopyTo($buffer, 0)
    [BitConverter]::GetBytes([int]$id).CopyTo($buffer, 4)
    [BitConverter]::GetBytes([int]$type).CopyTo($buffer, 8)
    $payload.CopyTo($buffer, 12)
    $stream.Write($buffer, 0, $buffer.Length)
    $stream.Flush()
}

function Read-Packet {
    $head = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $stream.Read($head, $read, 4 - $read)
        if ($n -le 0) { throw "RCON connection closed" }
        $read += $n
    }
    $length = [BitConverter]::ToInt32($head, 0)
    $body = New-Object byte[] $length
    $read = 0
    while ($read -lt $length) {
        $n = $stream.Read($body, $read, $length - $read)
        if ($n -le 0) { throw "RCON connection closed" }
        $read += $n
    }
    $id = [BitConverter]::ToInt32($body, 0)
    $type = [BitConverter]::ToInt32($body, 4)
    $text = [System.Text.Encoding]::UTF8.GetString($body, 8, $length - 10)
    [pscustomobject]@{ Id = $id; Type = $type; Body = $text }
}

try {
    Write-Packet 1 3 $Password
    $login = Read-Packet
    if ($login.Id -eq -1) { throw "RCON authentication failed" }

    Write-Packet 2 2 $Command
    while ($true) {
        try {
            $response = Read-Packet
        } catch [System.IO.IOException] {
            break
        }
        if ($response.Body) { Write-Output $response.Body }
        if ($response.Id -eq 2) { break }
    }
} finally {
    $stream.Dispose()
    $client.Dispose()
}
