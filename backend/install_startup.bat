@echo off
chcp 65001 >nul
echo 正在安装 Douyin Resolver 开机静默自启动服务...

set "TARGET_VBS=%~dp0start_silent.vbs"
set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT_PATH=%STARTUP_FOLDER%\DouyinResolverService.lnk"

powershell -NoProfile -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%SHORTCUT_PATH%'); $s.TargetPath = '%TARGET_VBS%'; $s.WorkingDirectory = '%~dp0'; $s.WindowStyle = 7; $s.Save()"

if exist "%SHORTCUT_PATH%" (
    echo [成功] 已添加开机自启快捷方式至：%SHORTCUT_PATH%
    echo 正在首次启动后台静默服务...
    wscript "%TARGET_VBS%"
    echo [完成] 服务已在后台静默运行中！电脑以后每次开机都会自动运行。
) else (
    echo [失败] 无法创建自启快捷方式，请尝试以管理员身份运行。
)
pause
