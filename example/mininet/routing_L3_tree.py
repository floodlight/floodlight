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
from mininet.topolib import TreeTopo
from mininet.util import irange

HOME_FOLDER = os.getenv('HOME')

"""
This script tests L3 routing functionality with tree topology 

1) Configure a virtual router with corresponding virtual interfaces 
2) Configure OVSes as switch members of that virtual router 
3) Configure hosts' interface with different subnet

Testing: host is reachable with each other across subnet

"""

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
            },
            {
                "interface-name" : "interface-6",
                "interface-ip" : "60.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-7",
                "interface-ip" : "70.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-8",
                "interface-ip" : "80.0.0.1",
                "interface-mask" : "255.255.255.0"
            },
            {
                "interface-name" : "interface-9",
                "interface-ip" : "90.0.0.1",
                "interface-mask" : "255.255.255.0"
            }
        ]
    }
    ret = rest_call('/wm/routing/gateway/' + name, data, 'POST')
    return ret


def addSwitchToGateway(name):
    data = {
        "gateway-name" : name,
        "gateway-ip" : "127.0.0.1",
        "switches": [
            {
                "dpid": "1"
            },
            {
                "dpid": "2"
            },
            {
                "dpid": "3"
            },
            {
                "dpid": "4"
            }
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


def startNetworkWithTreeTopo():
    global net
    topo = TreeTopo( depth=2, fanout=3 )
    net = Mininet(topo=topo, build=False)

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

    ret = addSwitchToGateway('mininet-gateway-1')
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

    host6 = net.getNodeByName('h6')
    host6.setIP('60.0.0.10', prefixLen=24)
    defaultGatewayIP6 = "60.0.0.1"
    configureDefaultGatewayForHost(host6, defaultGatewayIP6)

    host7 = net.getNodeByName('h7')
    host7.setIP('70.0.0.10', prefixLen=24)
    defaultGatewayIP7 = "70.0.0.1"
    configureDefaultGatewayForHost(host7, defaultGatewayIP7)

    host8 = net.getNodeByName('h8')
    host8.setIP('80.0.0.10', prefixLen=24)
    defaultGatewayIP8 = "80.0.0.1"
    configureDefaultGatewayForHost(host8, defaultGatewayIP8)


    host9 = net.getNodeByName('h9')
    host9.setIP('90.0.0.10', prefixLen=24)
    defaultGatewayIP9 = "90.0.0.1"
    configureDefaultGatewayForHost(host9, defaultGatewayIP9)

    # Set switch to OpenFlow 1.3 (Can change to any OpenFlow version)
    # switches = net.switches
    # for sw in switches:
    #     cmdStr = 'ovs-vsctl set bridge %s protocols=OpenFlow13' %sw
    #     sw.cmd(cmdStr)


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
    startNetworkWithTreeTopo()
    CLI(net)
    stopNetwork()

