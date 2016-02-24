#!/usr/bin/env python

import sys
sys.path.append('../../../target/gen-py')

from packetstreamer import PacketStreamer
from packetstreamer.ttypes import *

from thrift import Thrift
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol

try:

    # Make socket
    transport = TSocket.TSocket('localhost', 9090)

    # Buffering is critical. Raw sockets are very slow
    transport = TTransport.TFramedTransport(transport)

    # Wrap in a protocol
    protocol = TBinaryProtocol.TBinaryProtocol(transport)

    # Create a client to use the protocol encoder
    client = PacketStreamer.Client(protocol)

    # Connect!
    transport.open()

    while 1:
        packets = client.getPackets("session1")
        print 'session1 packets num: %d' % (len(packets))
        count = 1
        for packet in packets:
            print "Packet %d: %s"% (count, packet)
            if "FilterTimeout" in packet:
                sys.exit()
            count += 1 

    # Close!
    transport.close()

except Thrift.TException, tx:
    print '%s' % (tx.message)

except KeyboardInterrupt, e:
    print 'Bye-bye'
