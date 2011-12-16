#!/bin/bash

d=$(dirname $0)
MAIN_CLASS=$1
LIBRARIES=$2
[ "${MAIN_CLASS}" ] || { echo "Run 'ant eclipse' to generate Eclipse project files"; exit 1; }


cat >$d/.project <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>Floodlight</name>
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


cat >$d/.classpath <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="src" path="src/main/java" output="target/bin"/>
        <classpathentry kind="src" path="src/test/java" output="target/bin-test"/>
EOF
(
IFS=":"
for l in ${LIBRARIES}; do
cat >>$d/.classpath <<EOF
	<classpathentry exported="true" kind="lib" path="$l"/>
EOF
done
)
cat >>$d/.classpath <<EOF
	<classpathentry exported="true" kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
	<classpathentry kind="output" path="target/bin"/>
</classpath>
EOF
