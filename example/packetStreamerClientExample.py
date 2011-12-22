#!/usr/bin/python

import urllib2
import json
import re
import sys

from optparse import OptionParser

sys.path.append('~/floodlight/target/gen-py')
sys.path.append('~/floodlight/thrift/lib/py')

from packetstreamer import PacketStreamer
from packetstreamer.ttypes import *

from thrift import Thrift
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol

SESSIONID = 'sessionId'
usage = "usage: %prog [options]"
parser = OptionParser(usage=usage, version="%prog 1.0")
parser.add_option("-c", "--controller", dest="controller", metavar="CONTROLLER_IP",
                  default="127.0.0.1", help="controller's IP address")
parser.add_option("-m", "--mac", dest="mac", metavar="HOST_MAC",
                  help="The host mac address to trace the OF packets")

(options, args) = parser.parse_args()

def validateIp(ip):
    ipReg = ("(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" 
             "\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
             "\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
             "\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)")
    m = re.compile(ipReg).match(ip)
    if m:
        return True
    else :
        return False

def validateMac(mac):
    macReg = '([a-fA-F0-9]{2}:){5}[a-fA-F0-9]{2}' # same regex as above
    m = re.compile(macReg).match(mac)
    if m:
        return True
    else :
        return False

if not validateIp(options.controller):
    parser.error("Invalid format for ip address.")

if not options.mac:
    parser.error("-m or --mac option is required.")

if not validateMac(options.mac):
    parser.error("Invalid format for mac address. Format: xx:xx:xx:xx:xx:xx")

controller = options.controller
host = options.mac

url = 'http://%s:8080/wm/core/packettrace/json' % controller
filter = {'mac':host, 'direction':'both', 'period':1000}
post_data = json.dumps(filter)
request = urllib2.Request(url, post_data, {'Content-Type':'application/json'})
response_text = None

def terminateTrace(sid):
    global controller

    filter = {SESSIONID:sid, 'period':-1}
    post_data = json.dumps(filter)
    url = 'http://%s:8080/wm/core/packettrace/json' % controller
    request = urllib2.Request(url, post_data, {'Content-Type':'application/json'})
    try:
        response = urllib2.urlopen(request)
        response_text = response.read()
    except Exception, e:
        # Floodlight may not be running, but we don't want that to be a fatal
        # error, so we just ignore the exception in that case.
        print "Exception:", e

try: 
    response = urllib2.urlopen(request)
    response_text = response.read()
except Exception, e:
    # Floodlight may not be running, but we don't want that to be a fatal
    # error, so we just ignore the exception in that case.
    print "Exception:", e
    exit

if not response_text:
    print "Failed to start a packet trace session"
    sys.exit()

response_text = json.loads(response_text)

sessionId = None
if SESSIONID in response_text:
    sessionId = response_text[SESSIONID]
else:
    print "Failed to start a packet trace session"
    sys.exit()

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
        packets = client.getPackets(sessionId)
        for packet in packets:
            print "Packet: %s"% packet
            if "FilterTimeout" in packet:
                sys.exit()

except Thrift.TException, e:
    print '%s' % (e.message)
    terminateTrace(sessionId)

except KeyboardInterrupt, e:
    terminateTrace(sessionId)

# Close!
transport.close()

