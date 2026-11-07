Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "WinVer.nsh"
!include "FileFunc.nsh"
Name "Berkay Academic OS"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\BerkayAcademicOS"
InstallDirRegKey HKCU "Software\BerkayAcademicOS" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey /LANG=1055 "ProductName" "Berkay Academic OS"
VIAddVersionKey /LANG=1055 "FileDescription" "Berkay Academic OS Kurulumu"
VIAddVersionKey /LANG=1055 "FileVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=1055 "LegalCopyright" "Copyright (c) 2026 Berkay Academic OS"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TEXT "Bu sihirbaz Academic OS uygulamasını bilgisayarına kurar. Java çalışma ortamı pakete dahildir.$\r$\n$\r$\nKayıtların kullanıcı veri klasöründe tutulur; program güncellemelerinde korunur."
!define MUI_FINISHPAGE_RUN "$INSTDIR\BerkayAcademicOS.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Academic OS'u aç"
!insertmacro MUI_PAGE_WELCOME
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE VerifyInstallDirectory
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Turkish"

Function .onInit
  SetShellVarContext current
  SetRegView 64
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "Bu kurulum 64 bit Windows 10/11 içindir."
    SetErrorLevel 1
    Abort
  ${EndIf}
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_OK|MB_ICONSTOP "Windows 10 veya üzeri gerekiyor."
    SetErrorLevel 1
    Abort
  ${EndIf}
  ReadRegStr $0 HKCU "Software\BerkayAcademicOS" "InstallDir"
  ${If} $0 != ""
    StrCpy $INSTDIR "$0"
  ${EndIf}
  ${If} $0 != ""
  ${AndIf} ${FileExists} "$0\runtime\bin\java.exe"
    nsExec::ExecToStack /TIMEOUT=15000 '"$0\runtime\bin\java.exe" -Dacademic.os.packaged=true -cp "$0\app\lib\*" org.example.AppLauncher --check-instance'
    Pop $1
    Pop $2
    ${If} $1 != 0
      MessageBox MB_OK|MB_ICONSTOP "Güncellemeden önce Academic OS'u sistem tepsisinden tamamen kapat."
      SetErrorLevel 1
      Abort
    ${EndIf}
  ${EndIf}
FunctionEnd

Function VerifyInstallDirectory
  ${If} ${FileExists} "$INSTDIR\app\install-marker.ini"
    Return
  ${EndIf}
  FindFirst $0 $1 "$INSTDIR\*"
  loop:
  ${If} $1 == ""
    FindClose $0
    Return
  ${EndIf}
  ${If} $1 != "."
  ${AndIf} $1 != ".."
    FindClose $0
    MessageBox MB_OK|MB_ICONSTOP "Kurulum için boş bir klasör ya da mevcut Academic OS klasörünü seç."
    SetErrorLevel 1
    Abort
  ${EndIf}
  FindNext $0 $1
  Goto loop
FunctionEnd

Section "Academic OS" SEC_APP
  Call VerifyInstallDirectory
  ClearErrors
  ; Remove old managed binaries so an older versioned JAR cannot win on the classpath after an upgrade.
  ${If} ${FileExists} "$INSTDIR\app\install-marker.ini"
    ${If} ${FileExists} "$INSTDIR\app\lib\*"
      RMDir /r "$INSTDIR\app\lib"
      IfErrors install_failed
    ${EndIf}
    ${If} ${FileExists} "$INSTDIR\runtime\*"
      RMDir /r "$INSTDIR\runtime"
      IfErrors install_failed
    ${EndIf}
  ${EndIf}
  ClearErrors
  SetOutPath "$INSTDIR"
  File /r "${PAYLOAD}\*"
  IfErrors install_failed
  WriteINIStr "$INSTDIR\app\install-marker.ini" "AcademicOS" "Version" "${APP_VERSION}"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\Berkay Academic OS"
  CreateShortcut "$SMPROGRAMS\Berkay Academic OS\Academic OS.lnk" "$INSTDIR\BerkayAcademicOS.exe"
  CreateShortcut "$SMPROGRAMS\Berkay Academic OS\Kaldır.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortcut "$DESKTOP\Academic OS.lnk" "$INSTDIR\BerkayAcademicOS.exe"
  WriteRegStr HKCU "Software\BerkayAcademicOS" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "DisplayName" "Berkay Academic OS"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "Publisher" "Berkay Oltuluoglu"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "QuietUninstallString" '$\"$INSTDIR\Uninstall.exe$\" /S'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS" "NoRepair" 1
  Goto installed
  install_failed:
    MessageBox MB_OK|MB_ICONSTOP "Kurulum tamamlanamadı. Disk alanını ve uygulamanın tamamen kapalı olduğunu kontrol et."
    SetErrorLevel 1
    Abort
  installed:
SectionEnd

Function un.onInit
  SetShellVarContext current
  SetRegView 64
  nsExec::ExecToStack /TIMEOUT=15000 '"$INSTDIR\runtime\bin\java.exe" -Dacademic.os.packaged=true -cp "$INSTDIR\app\lib\*" org.example.AppLauncher --check-instance'
  Pop $0
  Pop $1
  ${If} $0 != 0
    MessageBox MB_OK|MB_ICONSTOP "Kaldırmadan önce Academic OS'u sistem tepsisinden tamamen kapat."
    SetErrorLevel 1
    Abort
  ${EndIf}
FunctionEnd

Section "Uninstall"
  Delete "$DESKTOP\Academic OS.lnk"
  RMDir /r "$SMPROGRAMS\Berkay Academic OS"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\agent-guide"
  Delete "$INSTDIR\academic-os.cmd"
  Delete "$INSTDIR\BerkayAcademicOS.exe"
  Delete "$INSTDIR\package-manifest.json"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\BerkayAcademicOS"
  DeleteRegValue HKCU "Software\BerkayAcademicOS" "InstallDir"
SectionEnd
