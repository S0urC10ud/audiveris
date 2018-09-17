; Script generated by the HM NIS Edit Script Wizard.

; HM NIS Edit Wizard helper defines
!define PRODUCT_NAME "Audiveris"
!define PRODUCT_VERSION "5.1"
!define PRODUCT_PUBLISHER "Audiveris Team"
!define PRODUCT_WEB_SITE "http://www.audiveris.org"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
!define PRODUCT_UNINST_ROOT_KEY "HKLM"
!define PRODUCT_STARTMENU_REGVAL "NSIS:StartMenuDir"

; MUI 1.67 compatible ------
!include "MUI.nsh"
; code from "http://nsis.sourceforge.net/File_Association"
!include "FileAssociation.nsh"


; MUI Settings
!define MUI_ABORTWARNING
!define MUI_ICON "..\..\res\icon-256.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

; Welcome page
!insertmacro MUI_PAGE_WELCOME
; License page
!insertmacro MUI_PAGE_LICENSE "..\..\LICENSE"
; Directory page
!insertmacro MUI_PAGE_DIRECTORY
; Start menu page
var ICONS_GROUP
!define MUI_STARTMENUPAGE_NODISABLE
!define MUI_STARTMENUPAGE_DEFAULTFOLDER "Audiveris"
!define MUI_STARTMENUPAGE_REGISTRY_ROOT "${PRODUCT_UNINST_ROOT_KEY}"
!define MUI_STARTMENUPAGE_REGISTRY_KEY "${PRODUCT_UNINST_KEY}"
!define MUI_STARTMENUPAGE_REGISTRY_VALUENAME "${PRODUCT_STARTMENU_REGVAL}"
!insertmacro MUI_PAGE_STARTMENU Application $ICONS_GROUP
; Instfiles page
!insertmacro MUI_PAGE_INSTFILES
; Finish page
!insertmacro MUI_PAGE_FINISH

; Uninstaller pages
!insertmacro MUI_UNPAGE_INSTFILES

; Language files
!insertmacro MUI_LANGUAGE "English"

; MUI end ------

Name "${PRODUCT_NAME} ${VERSION}"
OutFile "..\..\build\Setup_Audiveris_${VERSION}.exe"
InstallDir "$PROGRAMFILES\Audiveris"
ShowInstDetails show
ShowUnInstDetails show

Section "Hauptgruppe" SEC01
  SetOutPath "$INSTDIR\bin"
  SetOverwrite ifnewer
  File "..\..\build\distributions\Audiveris\bin\Audiveris"
  File "..\..\build\distributions\Audiveris\bin\Audiveris.bat"
  File "..\..\res\icon-256.ico"
  SetOutPath "$INSTDIR\lib"
  File "..\..\build\distributions\Audiveris\lib\*.jar"
 
  SetOutPath "$PROGRAMFILES\tesseract-ocr\tessdata"
  SetOverwrite ifnewer
  File "C:\Program Files (x86)\tesseract-ocr\tessdata\*.*"
  ; Shortcuts
  !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
  !insertmacro MUI_STARTMENU_WRITE_END
  
  MessageBox MB_ICONQUESTION|MB_YESNO|MB_DEFBUTTON1 "Do you want to associate '.omr' file extension with Audiveris?" IDNO +2
  ${registerExtension} "$INSTDIR\bin\${PRODUCT_NAME}.bat" ".omr" "OpticalMusicRecognition_File"
  WriteRegStr HKCR "OpticalMusicRecognition_File\DefaultIcon" "" "$INSTDIR\bin\icon-256.ico,0"

SectionEnd

Section -AdditionalIcons
  !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
  WriteIniStr "$INSTDIR\${PRODUCT_NAME}.url" "InternetShortcut" "URL" "${PRODUCT_WEB_SITE}"
  CreateDirectory "$SMPROGRAMS\$ICONS_GROUP"
  CreateShortCut "$SMPROGRAMS\$ICONS_GROUP\Audiveris.lnk" "$INSTDIR\bin\${PRODUCT_NAME}.bat" "" "$INSTDIR\bin\icon-256.ico"
  CreateShortCut "$SMPROGRAMS\$ICONS_GROUP\Website.lnk" "$INSTDIR\${PRODUCT_NAME}.url"
  CreateShortCut "$SMPROGRAMS\$ICONS_GROUP\Uninstall.lnk" "$INSTDIR\uninst.exe"
  !insertmacro MUI_STARTMENU_WRITE_END
SectionEnd

Section -Post
  WriteUninstaller "$INSTDIR\uninst.exe"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayName" "$(^Name)"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "UninstallString" "$INSTDIR\uninst.exe"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "URLInfoAbout" "${PRODUCT_WEB_SITE}"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
SectionEnd


Function un.onUninstSuccess
  HideWindow
  MessageBox MB_ICONINFORMATION|MB_OK "$(^Name) has been properly uninstalled."
FunctionEnd

Function un.onInit
  MessageBox MB_ICONQUESTION|MB_YESNO|MB_DEFBUTTON2 "Do you want to uninstall $(^Name) and all their components?" IDYES +2
  Abort
  MessageBox MB_ICONQUESTION|MB_YESNO|MB_DEFBUTTON2 "Do you want to keep the configuration data?" IDYES +2
  RmDir /r "$AppData\AudiverisLtd\audiveris"
FunctionEnd

Section Uninstall
  Delete "$INSTDIR\${PRODUCT_NAME}.url"
  Delete "$INSTDIR\uninst.exe"
  Delete "$INSTDIR\lib\*.*"
  Delete "$INSTDIR\bin\Audiveris.bat"
  Delete "$INSTDIR\bin\Audiveris"
  Delete "$INSTDIR\bin\icon-256.ico"

  Delete "$SMPROGRAMS\Audiveris\Uninstall.lnk"
  Delete "$SMPROGRAMS\Audiveris\Website.lnk"
  Delete "$SMPROGRAMS\Audiveris\Audiveris.lnk"

  RMDir "$SMPROGRAMS\Audiveris"
  RMDir "$INSTDIR\lib"
  RMDir "$INSTDIR\bin"
  RMDir "$INSTDIR"

  DeleteRegKey ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}"
  ${unregisterExtension} ".omr" "OpticalMusicRecognition_File"
  DeleteRegKey HKCR "OpticalMusicRecognition_File" ; brute force delete
  SetAutoClose true
SectionEnd