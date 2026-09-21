@echo off
chcp 65001 >nul
echo 正在停止后台解析服务...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3000" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo 已终止端口 3000 的服务进程 PID: %%a
)
echo 停止完成。
pause
