Unicode true
!include "FileFunc.nsh"
Name "Berkay Academic OS"
!ifdef APP_ICON
Icon "${APP_ICON}"
!endif
OutFile "${OUTPUT}"
SilentInstall silent
RequestExecutionLevel user
AutoCloseWindow true
Section
  SetOutPath "$EXEDIR"
  ClearErrors
  ${GetParameters} $0
  ExecWait '"$EXEDIR\runtime\bin\javaw.exe" -Dacademic.os.packaged=true -cp "$EXEDIR\app\lib\*" org.example.AppLauncher $0' $1
  IfErrors 0 +2
    MessageBox MB_OK|MB_ICONSTOP "Academic OS başlatılamadı. Uygulamayı yeniden kurmayı deneyebilirsin."
SectionEnd
