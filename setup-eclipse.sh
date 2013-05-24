#!/bin/bash
d=$(dirname "$0")
MAIN_CLASS=$1
LIBRARIES=$2
[ "${MAIN_CLASS}" ] || { echo "Run 'ant eclipse' to generate Eclipse project files"; exit 1; }


cat >"$d/.project" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>floodlight</name>
	<comment></comment>
	<projects>
	</projects>
	<buildSpec>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
	</buildSpec>
	<natures>
		<nature>org.eclipse.jdt.core.javanature</nature>
	</natures>
</projectDescription>
EOF

cat >"$d/Floodlight-Default-Conf.launch" << EOF
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication">
    <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS">
        <listEntry value="/floodlight/src/main/java/net/floodlightcontroller/core/Main.java"/>
    </listAttribute>
    <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES">
        <listEntry value="1"/>
    </listAttribute>
    <stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="net.floodlightcontroller.core.Main"/>
    <stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="floodlight"/>
    <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-ea"/>
</launchConfiguration>
EOF

cat > "$d/Floodlight-Quantum-Conf.launch" << EOF
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication">
    <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS">
        <listEntry value="/floodlight/src/main/java/net/floodlightcontroller/core/Main.java"/>
    </listAttribute>
    <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES">
        <listEntry value="1"/>
    </listAttribute>
    <stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="net.floodlightcontroller.core.Main"/>
    <stringAttribute key="org.eclipse.jdt.launching.PROGRAM_ARGUMENTS" value="-cf src/main/resources/quantum.properties"/>
    <stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="floodlight"/>
    <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-ea"/>
</launchConfiguration>
EOF

cat >"$d/SyncClient.launch" << EOF
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication">
<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS">
<listEntry value="/floodlight/src/main/java/org/sdnplatform/sync/client/SyncClient.java"/>
</listAttribute>
<listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES">
<listEntry value="1"/>
</listAttribute>
<stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="org.sdnplatform.sync.client.SyncClient"/>
<stringAttribute key="org.eclipse.jdt.launching.PROGRAM_ARGUMENTS" value="--hostname localhost --port 6642 --authScheme CHALLENGE_RESPONSE --keyStorePath /opt/bigswitch/floodlight/configuration/auth_credentials.jceks --keyStorePassword dcbc178a0a3a8674f048ac86372ac456"/>
<stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="bigfloodlight"/>
<stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-ea"/>
</launchConfiguration>
EOF

cat >"$d/.classpath" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="src" path="src/main/java" output="target/bin"/>
	<classpathentry kind="src" path="src/main/resources"/>
        <classpathentry kind="src" path="src/test/java" output="target/bin-test"/>
	<classpathentry kind="src" path="src/test/resources"/>
        <classpathentry kind="src" path="lib/gen-java" output="target/bin"/>
EOF
(
IFS=":"
for l in ${LIBRARIES}; do
cat >>$d/.classpath <<EOF
	<classpathentry exported="true" kind="lib" path="$l"/>
EOF
done
)
cat >>"$d/.classpath" <<EOF
	<classpathentry exported="true" kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
	<classpathentry kind="output" path="target/bin"/>
</classpath>
EOF

mkdir -p $d/.settings
cat >$d/.settings/edu.umd.cs.findbugs.core.prefs  <<EOF
excludefilter0=findbugs-exclude.xml|true
filter_settings=Medium|BAD_PRACTICE,CORRECTNESS,MALICIOUS_CODE,MT_CORRECTNESS,PERFORMANCE,SECURITY,STYLE|false|20
filter_settings_neg=NOISE,I18N,EXPERIMENTAL|
EOF
