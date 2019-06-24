@ECHO OFF

SET d=%cd%
SET MAIN_CLASS=%1
SET LIBRARIES=%2

IF "%MAIN_CLASS%" == "" (
ECHO "Run 'ant eclipse' to generate Eclipse project files";
EXIT 1;
)

(
ECHO ^<?xml version="1.0" encoding="UTF-8"?^>
ECHO ^<projectDescription^>
ECHO 	^<name^>floodlight^</name^>
ECHO 	^<comment^>^</comment^>
ECHO 	^<projects^>
ECHO 	^</projects^>
ECHO 	^<buildSpec^>
ECHO 		^<buildCommand^>
ECHO 			^<name^>org.eclipse.jdt.core.javabuilder^</name^>
ECHO 			^<arguments^>
ECHO 			^</arguments^>
ECHO 		^</buildCommand^>
ECHO 	^</buildSpec^>
ECHO 	^<natures^>
ECHO 		^<nature^>org.eclipse.jdt.core.javanature^</nature^>
ECHO 	^</natures^>
ECHO ^</projectDescription^>
) > "%d%/.project"

(
ECHO ^<?xml version="1.0" encoding="UTF-8" standalone="no"?^>
ECHO ^<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication"^>
ECHO     ^<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS"^>
ECHO         ^<listEntry value="/floodlight/src/main/java/net/floodlightcontroller/core/Main.java"/^>
ECHO     ^</listAttribute^>
ECHO     ^<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES"^>
ECHO         ^<listEntry value="1"/^>
ECHO     ^</listAttribute^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="net.floodlightcontroller.core.Main"/^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="floodlight"/^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-ea"/^>
ECHO ^</launchConfiguration^>
) > "%d%/Floodlight-Default-Conf.launch"

(
ECHO ^<?xml version="1.0" encoding="UTF-8" standalone="no"?^>
ECHO ^<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication"^>
ECHO     ^<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS"^>
ECHO         ^<listEntry value="/floodlight/src/main/java/net/floodlightcontroller/core/Main.java"/^>
ECHO     ^</listAttribute^>
ECHO     ^<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES"^>
ECHO         ^<listEntry value="1"/^>
ECHO     ^</listAttribute^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="net.floodlightcontroller.core.Main"/^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.PROGRAM_ARGUMENTS" value="-cf src/main/resources/quantum.properties"/^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="floodlight"/^>
ECHO     ^<stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-ea"/^>
ECHO ^</launchConfiguration^>
) > "%d%/Floodlight-Quantum-Conf.launch"

(
ECHO ^<?xml version="1.0" encoding="UTF-8" standalone="no"?^>
ECHO ^<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication"^>
ECHO ^<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS"^>
ECHO ^<listEntry value="/floodlight/src/main/java/org/sdnplatform/sync/client/SyncClient.java"/^>
ECHO ^</listAttribute^>
ECHO ^<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES"^>
ECHO ^<listEntry value="1"/^>
ECHO ^</listAttribute^>
ECHO ^<stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="org.sdnplatform.sync.client.SyncClient"/^>
ECHO ^<stringAttribute key="org.eclipse.jdt.launching.PROGRAM_ARGUMENTS" value="--hostname localhost --port 6642 --authScheme CHALLENGE_RESPONSE --keyStorePath /opt/bigswitch/floodlight/configuration/auth_credentials.jceks --keyStorePassword dcbc178a0a3a8674f048ac86372ac456"/^>
ECHO ^<stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="bigfloodlight"/^>
ECHO ^<stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-ea"/^>
ECHO ^</launchConfiguration^>
) > "%d%/SyncClient.launch" 

(
ECHO ^<?xml version="1.0" encoding="UTF-8"?^>
ECHO ^<classpath^>
ECHO 	^<classpathentry kind="src" path="src/main/java" output="target/bin"/^>
ECHO 	^<classpathentry kind="src" path="src/main/resources"/^>
ECHO         ^<classpathentry kind="src" path="src/test/java" output="target/bin-test"/^>
ECHO 	^<classpathentry kind="src" path="src/test/resources"/^>
ECHO         ^<classpathentry kind="src" path="lib/gen-java" output="target/bin"/^>
) > "%d%/.classpath"

CALL :PARSE "%LIBRARIES%"
GOTO :END

:PARSE
SETLOCAL
SET list=%1
SET list=%list:"=%
FOR /F "tokens=1* delims=:" %%a IN ("%list%") DO (
  IF NOT "%%a" == "" CALL :SUB %%a
  IF NOT "%%b" == "" CALL :PARSE "%%b"
)
ENDLOCAL
EXIT /B

:SUB
SETLOCAL
(
ECHO 	^<classpathentry exported="true" kind="lib" path="%1"/^>
) >> "%d%/.classpath"
ENDLOCAL
EXIT /B

:END
(
ECHO 	^<classpathentry exported="true" kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/^>
ECHO 	^<classpathentry kind="output" path="target/bin"/^>
ECHO ^</classpath^>
) >> "%d%/.classpath"

MKDIR "%d%/.settings"
(
ECHO excludefilter0=findbugs-exclude.xml^|true
ECHO filter_settings=Medium^|BAD_PRACTICE,CORRECTNESS,MALICIOUS_CODE,MT_CORRECTNESS,PERFORMANCE,SECURITY,STYLE^|false^|20
ECHO filter_settings_neg=NOISE,I18N,EXPERIMENTAL^|
) > "%d%/.settings/edu.umd.cs.findbugs.core.prefs"
