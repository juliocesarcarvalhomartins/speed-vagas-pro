Option Explicit
Dim sh, fso, base, launcherDir, bootstrap, splash, readyFile, errorFile, statusFile, logFile
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
launcherDir = base & "\launcher"
bootstrap = launcherDir & "\bootstrap.ps1"
splash = launcherDir & "\splash.ps1"
readyFile = launcherDir & "\.ready"
errorFile = launcherDir & "\.error"
statusFile = launcherDir & "\.status"
logFile = launcherDir & "\launcher.log"

If Not fso.FileExists(bootstrap) Then
  MsgBox "Arquivo do launcher nao encontrado:" & vbCrLf & bootstrap, 16, "SPEED VAGAS PRO"
  WScript.Quit 1
End If
If Not fso.FileExists(splash) Then
  MsgBox "Arquivo da tela de abertura nao encontrado:" & vbCrLf & splash, 16, "SPEED VAGAS PRO"
  WScript.Quit 1
End If

' Limpa marcadores antigos ANTES de abrir o splash. Isso evita o splash
' interpretar um .ready antigo como uma inicializacao concluida.
On Error Resume Next
If fso.FileExists(readyFile) Then fso.DeleteFile readyFile, True
If fso.FileExists(errorFile) Then fso.DeleteFile errorFile, True
If fso.FileExists(statusFile) Then fso.DeleteFile statusFile, True
On Error GoTo 0

sh.Run "powershell.exe -NoLogo -NoProfile -STA -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & splash & """", 0, False
WScript.Sleep 300
sh.Run "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & bootstrap & """", 0, False
