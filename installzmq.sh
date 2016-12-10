#!/bin/bash

set -x
set -i

if [[ "$OSTYPE" == "darwin"* ]];
then
	echo "Installing ZMQ on Mac OSX..."
	sudo date

	# Git & g++:
	echo "Please install clang/g++ (either by installing XCode or by some other means)..."
	xcode-select --install

	# AutoConf
	curl -OL http://ftpmirror.gnu.org/autoconf/autoconf-2.69.tar.gz
	tar -xzf autoconf-2.69.tar.gz
	cd autoconf-2.69
	./configure && make && sudo make install

	# AutoMake
	curl -OL http://ftpmirror.gnu.org/automake/automake-1.14.tar.gz
	tar -xzf automake-1.14.tar.gz
	cd automake-1.14
	./configure && make && sudo make install

	# Libtool
	curl -OL http://ftpmirror.gnu.org/libtool/libtool-2.4.2.tar.gz
	tar -xzf libtool-2.4.2.tar.gz
	cd libtool-2.4.2
	./configure && make && sudo make install

	# UUID-dev
	git clone https://github.com/mecke/ossp-uuid
	cd ossp-uuid
	./configure && make && sudo make install

	# pkg-config
	git clone https://github.com/Distrotech/pkg-config
	cd pkg-config
	./configure && make && sudo make install

	# Valgrind
#	git clone https://github.com/svn2github/valgrind
#	cd valgrind
#	./autogen.sh && ./configure && make && sudo make install

	# Asciidoc
	curl -OL http://sourceforge.net/projects/asciidoc/files/asciidoc/8.6.9/asciidoc-8.6.9.tar.gz
	tar -zxf asciidoc-8.6.9.tar.gz
	cd asciidoc-8.6.9
	./configure && sudo make install

#	ulimit -n 1200
	git clone git://github.com/zeromq/libzmq.git
	cd libzmq
	git checkout 8cf4832e01cf9f3161157373e9c66e7be18ae0bb
	./autogen.sh
	./configure
	make
	make check
	echo "tests/system would have failed at this point because it wasn't able to create
	1000 sockets. This is OK, and is expected. If you want the test to pass run 'ulimit -n 1200' before running make check...'"
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

	rm -rf autoconf-2.69/
	rm  -f autoconf-2.69.tar.gz
	rm -f asciidoc-8.6.9.tar.gz
	rm -rf asciidoc-8.6.9/
	rm -rf ossp-uuid/
	rm -rf pkg-config/

else

	echo "Installing ZMQ on linux, ensure that you have the aptitude package manager..."
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

fi

set +i
set +x
