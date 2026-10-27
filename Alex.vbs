' Silent Alex launcher: no console, but UI window visible (style 1 = normal).
Option Explicit
Dim sh, fso, root, pyw, app, cmd
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)
pyw = root & "\backend\venv\Scripts\pythonw.exe"
If Not fso.FileExists(pyw) Then
  pyw = root & "\venv\Scripts\pythonw.exe"
End If
If Not fso.FileExists(pyw) Then
  pyw = "pythonw.exe"
End If
app = root & "\backend\desktop_app.py"
sh.Environment("PROCESS")("JARVIS_HOME") = root
sh.Environment("PROCESS")("HF_HOME") = "D:\JARVIS-cache\huggingface"
sh.Environment("PROCESS")("TTS_HOME") = "D:\JARVIS-cache\tts"
sh.Environment("PROCESS")("HF_HUB_OFFLINE") = "1"
sh.Environment("PROCESS")("TRANSFORMERS_OFFLINE") = "1"
sh.Environment("PROCESS")("HF_HUB_DISABLE_TELEMETRY") = "1"
sh.Environment("PROCESS")("COQUI_TOS_AGREED") = "1"
sh.Environment("PROCESS")("JARVIS_DESKTOP") = "1"
cmd = """" & pyw & """ """ & app & """"
sh.CurrentDirectory = root & "\backend"
' 1 = normal window (0 hid the WebView UI as well)
sh.Run cmd, 1, False
