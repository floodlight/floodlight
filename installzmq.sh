#/bin/bash
set -x

sudo add-apt-repository ppa:git-core/ppa -y
sudo apt-get update
sudo apt-get -y install git ant g++ valgrind
sudo apt-get -y install build-essential uuid-dev libtool autoconf automake pkg-config asciidoc xmlto
git clone git://github.com/zeromq/libzmq.git
cd libzmq
git checkout 8cf4832e01cf9f3161157373e9c66e7be18ae0bb
./autogen.sh
./configure
make
make check
sudo make install
sudo ldconfig
cd ..
git clone git://github.com/zeromq/jzmq
cd jzmq
git checkout 256ccec32b7cd99bb1edb7fc42c1e1a79f88cd68
cd jzmq-jni/
./autogen.sh
./configure
make
sudo make install
cd ../..
rm -rf jzmq libzmq
cp /usr/local/share/java/zmq.jar lib/jzmq-3.1.0.jar
cp /usr/local/libjzmq* lib/

set +x
