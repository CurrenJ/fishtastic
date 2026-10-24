# Screenshot the main window of a process, for diagnosing a client parked on a screen; optionally
# then deliver keys to that window.
# Usage: window_shot.ps1 -ProcessId <pid> -Out <png> [-Keys TAB,TAB,ENTER]
#
# Keys are POSTED to the window handle (WM_KEYDOWN/WM_KEYUP), never sent as global input: SendKeys /
# SendInput type into whatever window has focus, and Windows may refuse to give Minecraft focus, so
# global input can land in the owner's other apps. GLFW maps keys by the scan code in lParam, so
# each key carries its scan code.
param([Parameter(Mandatory)][int]$ProcessId, [Parameter(Mandatory)][string]$Out, [string]$Keys)
$keyList = if ($Keys) { $Keys -split ',' } else { @() }   # new name: $Keys is [string]-typed and would re-join the array
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Win {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
}
'@
$h = (Get-Process -Id $ProcessId).MainWindowHandle
if ($h -eq [IntPtr]::Zero) { Write-Error "process $ProcessId has no main window"; exit 1 }

# PrintWindow with PW_RENDERFULLCONTENT captures the window's own content even when it is covered.
$r = New-Object Win+RECT
[Win]::GetWindowRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
[Win]::PrintWindow($h, $hdc, 2) | Out-Null
$g.ReleaseHdc($hdc)
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)

$codes = @{ TAB = @(0x09, 0x0F); ENTER = @(0x0D, 0x1C); ESCAPE = @(0x1B, 0x01) }
foreach ($k in $keyList) {
    $vk, $scan = $codes[$k.ToUpper()]
    if ($null -eq $vk) { Write-Error "unknown key $k"; exit 1 }
    $down = 1 -bor ($scan -shl 16)
    $up = $down -bor (1 -shl 30) -bor (1 -shl 31)
    [Win]::PostMessage($h, 0x0100, [IntPtr]$vk, [IntPtr]$down) | Out-Null   # WM_KEYDOWN
    Start-Sleep -Milliseconds 100
    [Win]::PostMessage($h, 0x0101, [IntPtr]$vk, [IntPtr][long]$up) | Out-Null   # WM_KEYUP
    Start-Sleep -Milliseconds 250
}
