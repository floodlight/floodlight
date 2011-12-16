#!/bin/bash

set -ex

DOWNLOADS=${DOWNLOADS:-${HOME}/Downloads}
mkdir -p ${DOWNLOADS}

THRIFT_VERSION=0.7.0
THRIFT_PKG=thrift-${THRIFT_VERSION}.tar.gz
THRIFT_PKG_HOST=http://apache.osuosl.org/thrift/${THRIFT_VERSION}

if [ ! -d thrift ]; then
    if [ ! -f ${DOWNLOADS}/${THRIFT_PKG} ]; then
        if [ -e /usr/bin/curl ]; then
	    curl ${THRIFT_PKG_HOST}/${THRIFT_PKG} -o ${DOWNLOADS}/${THRIFT_PKG}
        else
            wget -nc http://download.nextag.com/apache/thrift/${THRIFT_VERSION}/${THRIFT_PKG}
            mv ${THRIFT_PKG} ${DOWNLOADS}/.
        fi
    fi
    rm -rf thrift-${THRIFT_VERSION}
    tar -z -x -f ${DOWNLOADS}/${THRIFT_PKG}
    mv thrift-${THRIFT_VERSION} thrift
fi

if [ ! -f lib/libthrift-${THRIFT_VERSION}.jar -o ! -f thrift/compiler/cpp/thrift ]; then
  (
      cd thrift
      chmod +x ./configure 
      ./configure --with-cpp=no --with-erlang=no --with-perl=no --with-php=no --with-php_extension=no --with-ruby=no --with-haskell=no 
      ARCHFLAGS="-arch i386 -arch x86_64" make -j4 all 
      cd lib/java 
      ant
  )
  mkdir -p lib
  cp thrift/lib/java/build/libthrift-${THRIFT_VERSION}.jar lib/
fi

cp lib/libthrift-${THRIFT_VERSION}.jar packetstreamerd/lib/
(
    cd packetstreamerd
    ../thrift/compiler/cpp/thrift --gen py --gen java packetstreamer.thrift
    cd java; ant
)
