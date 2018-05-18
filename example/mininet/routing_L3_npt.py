#!/usr/bin/python
import json

import httplib
import os
import subprocess
import time
from mininet.cli import CLI
from mininet.log import setLogLevel, info
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
from mininet.util import irange

HOME_FOLDER = os.getenv('HOME')


"""
This script tests L3 routing functionality with linear topology

1) Configure a virtual router with corresponding virtual interfaces
2) Configure OVSes as node-port-tuple members of that virtual router 
3) Configure hosts' interface with different subnet

Testing: host is reachable with each other across subnet

"""

class LinearTopo(Topo):
    """
    construct a network of N hosts and N-1 switches, connected as follows:
    h1 <-> s1 <-> s2 .. sN-1
           |       |    |
           h2      h3   hN

    """
    def __init__(self, N, **params):
        Topo.__init__(self, **params)

        hosts = [ self.addHost( 'h%s' % h )
                  for h in irange( 1, N ) ]

        switches = [ self.addSwitch( 's%s' % s, protocols=["OpenFlow13"] )
                     for s in irange( 1, N - 1 ) ]

        # Wire up switches
        last = None
        for switch in switches:
            if last:
                self.addLink( last, switch )
            last = switch


        # Wire up hosts
        self.addLink( hosts[ 0 ], switches[ 0 ] )
        for host, switch in zip( hosts[ 1: ], switches ):
            self.addLink( host, switch )



def getControllerIP():
    guest_ip = subprocess.check_output("/sbin/ifconfig eth1 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1}'",
                                       shell=True)
    split_ip = guest_ip.split('.')
    split_ip[3] = '1'
    return '.'.join(split_ip)


def rest_call(path, data, action):
    headers = {
        'Content-type': 'application/json',
        'Accept'      : 'application/json',
    }
    body = json.dumps(data)

    conn = httplib.HTTPConnection(getControllerIP(), 8080)
    conn.request(action, path, body, headers)
    response = conn.getresponse()

    ret = (response.status, response.reason, response.read())
    conn.close()
    return ret


def addVirtualGateway(name):
    data = {
        "gateway-name" : name,
        "gateway-mac" : "aa:bb:cc:dd:ee:ff"
    }
    ret = rest_call('/wm/routing/gateway', data, 'POST')
    return ret


def addInterfaceToGateway(name):
    data = {
        "interfaces" : [
            {
                "interface-name" : "interface-1",
                "interface-ip" : "10.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-2",
                "interface-ip" : "20.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-3",
                "interface-ip" : "30.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-4",
                "interface-ip" : "40.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-5",
                "interface-ip" : "50.0.0.1",
                "interface-mask" : "255.255.255.0"
            }
        ]
    }
    ret = rest_call('/wm/routing/gateway/' + name, data, 'POST')
    return ret


def addNodePortTupleToGateway(name):
    data = {
        "gateway-name" : name,
        "gateway-ip" : "127.0.0.1",
        "switchports": [
            {
                "dpid": "1",
                "port": "1"
            },
            {
                "dpid": "1",
                "port": "2"
            },
            {
                "dpid": "1",
                "port": "3"
            },
            {
                "dpid": "2",
                "port": "1"
            },
            {
                "dpid": "2",
                "port": "2"
            },
            {
                "dpid": "2",
                "port": "3"
            },
            {
                "dpid": "3",
                "port": "1"
            },
            {
                "dpid": "3",
                "port": "2"
            },
            {
                "dpid": "3",
                "port": "3"
            },
            {
                "dpid": "4",
                "port": "1"
            },
            {
                "dpid": "4",
                "port": "2"
            },
            {
                "dpid": "4",
                "port": "3"
            },
            {
                "dpid": "5",
                "port": "1"
            },
            {
                "dpid": "5",
                "port": "2"
            },
        ]
    }
    ret = rest_call('/wm/routing/gateway/' + name, data, 'POST')
    return ret


def configureDefaultGatewayForHost(host, defaultGatewayIP):
    host.cmd('route add default gw ' + defaultGatewayIP);


def enableL3Routing():
    data = {
        "enable" : "true"
    }
    ret = rest_call('/wm/routing/config', data, 'POST')
    return ret


def disableL3Routing():
    data = {
        "enable" : "false"
    }
    ret = rest_call('/wm/routing/config', data, 'POST')
    return ret


def startNetworkWithLinearTopo( hostCount ):
    global net
    net = Mininet(topo=LinearTopo(hostCount), build=False)

    remote_ip = getControllerIP()
    info('** Adding Floodlight Controller\n')
    net.addController('c1', controller=RemoteController,
                      ip=remote_ip, port=6653)

    # Build the network
    net.build()
    net.start()

    # Start L3 Routing
    ret = enableL3Routing()
    print (ret)

    ret = addVirtualGateway('mininet-gateway-1')
    print (ret)

    ret = addInterfaceToGateway('mininet-gateway-1')
    print (ret)

    ret = addNodePortTupleToGateway('mininet-gateway-1')
    print (ret)

    # Need to configure default gw for host
    host1 = net.getNodeByName('h1')
    host1.setIP('10.0.0.10', prefixLen=24)
    defaultGatewayIP1 = "10.0.0.1"
    configureDefaultGatewayForHost(host1, defaultGatewayIP1)

    host2 = net.getNodeByName('h2')
    host2.setIP('20.0.0.10', prefixLen=24)
    defaultGatewayIP2 = "20.0.0.1"
    configureDefaultGatewayForHost(host2, defaultGatewayIP2)

    host3 = net.getNodeByName('h3')
    host3.setIP('30.0.0.10', prefixLen=24)
    defaultGatewayIP3 = "30.0.0.1"
    configureDefaultGatewayForHost(host3, defaultGatewayIP3)

    host4 = net.getNodeByName('h4')
    host4.setIP('40.0.0.10', prefixLen=24)
    defaultGatewayIP4 = "40.0.0.1"
    configureDefaultGatewayForHost(host4, defaultGatewayIP4)

    host5 = net.getNodeByName('h5')
    host5.setIP('50.0.0.10', prefixLen=24)
    defaultGatewayIP5 = "50.0.0.1"
    configureDefaultGatewayForHost(host5, defaultGatewayIP5)


def clearGatewayInstance(name):
    data = {}
    ret = rest_call('/wm/routing/gateway/' + name, data, 'DELETE')
    return ret


def stopNetwork():
    if net is not None:
        info('** Tearing down network\n')
        clearGatewayInstance('mininet-gateway-1')
        net.stop()


if __name__ == '__main__':
    setLogLevel('info')
    startNetworkWithLinearTopo(6)
    CLI(net)
    stopNetwork()

