#!/bin/sh
echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee /etc/apt/sources.list.d/webupd8team-java.list
echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
apt-get update
apt-get install oracle-java8-installer
mv -f etc/local_policy.jar ${JAVA_HOME}/jre/lib/security/local_policy.jar
mv -f etc/US_export_policy.jar ${JAVA_HOME}/jre/lib/security/US_export_policy.jar
exit
