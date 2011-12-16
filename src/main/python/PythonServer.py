#!/usr/bin/env python

import sys
import logging
sys.path.append('../../../target/gen-py')

from packetstreamer import PacketStreamer
from packetstreamer.ttypes import *

from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol
from thrift.server import TServer

class PacketStreamerHandler:
  def __init__(self):
    logging.handlers.codecs = None
    self.log = logging.getLogger("packetstreamer")
    self.log.setLevel(logging.DEBUG)
    handler = logging.handlers.SysLogHandler("/dev/log")
    handler.setFormatter(logging.Formatter("%(name)s: %(levelname)s %(message)s"))
    self.log.addHandler(handler)

  def ping(self):
    self.log.debug('ping()')
    return true

  def pushPacketSync(self, packet):
    self.log.debug('receive a packet synchronously: %s' %(packet)
    return 0

  def pushPacketAsync(self, packet):
    self.log.debug('receive a packet Asynchronously: %s' %(packet)

handler = PacketStreamerHandler()
processor = PacketStreamer.Processor(handler)
transport = TSocket.TServerSocket(9090)
tfactory = TTransport.TBufferedTransportFactory()
pfactory = TBinaryProtocol.TBinaryProtocolFactory()

server = TServer.TSimpleServer(processor, transport, tfactory, pfactory)

# You could do one of these for a multithreaded server
#server = TServer.TThreadedServer(processor, transport, tfactory, pfactory)
#server = TServer.TThreadPoolServer(processor, transport, tfactory, pfactory)

print 'Starting the server...'
server.serve()
print 'done.'
