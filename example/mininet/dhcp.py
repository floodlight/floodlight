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

HOME_FOLDER = os.getenv('HOME')


class DHCPTopo(Topo):
    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)
        h1 = self.addHost('h1', ip='10.0.0.10/24')
        h2 = self.addHost('h2', ip='10.0.0.20/24')
        switch = self.addSwitch('s1')
        self.addLink(h1, switch)
        self.addLink(h2, switch)


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

def addDHCPInstance(name):
    data = {
        "name"         : name,
        "start-ip"     : "10.0.0.100",
        "end-ip"       : "10.0.0.200",
        "server-id"    : "10.0.0.2",
        "server-mac"   : "aa:bb:cc:dd:ee:ff",
        "router-ip"    : "10.0.0.1",
        "broadcast-ip" : "10.0.0.255",
        "subnet-mask"  : "255.255.255.0",
        "lease-time"   : "60",
        "rebind-time"  : "60",
        "renew-time"   : "60",
        "ip-forwarding": "true",
        "domain-name"  : "mininet-domain-name"
    }
    ret = rest_call('/wm/dhcp/instance', data, 'POST')
    return ret


def addNodePortTupleToDHCPInstance(name):
    data = {
        "switchports": [
            {
                "dpid": "1",
                "port": "1"
            }
        ]
    }
    ret = rest_call('/wm/dhcp/instance/' + name, data, 'POST')
    return ret

def enableDHCPServer():
    data = {
        "enable" : "true"
    }
    ret = rest_call('/wm/dhcp/config', data, 'POST')
    return ret

# DHCP client functions

def startDHCPclient(host):
    "Start DHCP client on host"
    intf = host.defaultIntf()
    host.cmd('dhclient -v -d -r', intf)
    host.cmd('dhclient -v -d 1> /tmp/dhclient.log 2>&1', intf, '&')


def stopDHCPclient(host):
    host.cmd('kill %dhclient')


def waitForIP(host):
    "Wait for an IP address"
    info('*', host, 'waiting for IP address')
    while True:
        host.defaultIntf().updateIP()
        if host.IP():
            break
        info('.')
        time.sleep(1)
    info('\n')
    info('*', host, 'is now using',
         host.cmd('grep nameserver /etcresolv.conf'))


def mountPrivateResolvconf(host):
    "Create/mount private /etc/resolv.conf for host"
    etc = '/tmp/etc-%s' % host
    host.cmd('mkdir -p', etc)
    host.cmd('mount --bind /etc', etc)
    host.cmd('mount -n -t tmpfs tmpfs /etc')
    host.cmd('ln -s %s/* /etc/' % etc)
    host.cmd('rm /etc/resolv.conf')
    host.cmd('cp %s/resolv.conf /etc/' % etc)


def unmountPrivateResolvconf(host):
    "Unmount private /etc dir for host"
    etc = '/tmp/etc-%s' % host
    host.cmd('umount /etc')
    host.cmd('umount', etc)
    host.cmd('rmdir', etc)


def startNetwork():
    # Create the network from a given topology without building it yet
    global net
    net = Mininet(topo=DHCPTopo(), build=False)

    remote_ip = getControllerIP()
    info('** Adding Floodlight Controller\n')
    net.addController('c1', controller=RemoteController,
                      ip=remote_ip, port=6653)

    # Build the network
    net.build()
    net.start()

    # Start DHCP
    ret = enableDHCPServer()
    print(ret)

    ret = addDHCPInstance('mininet-dhcp')
    print(ret)

    ret = addNodePortTupleToDHCPInstance('mininet-dhcp')
    print(ret)

    h1 = net.get('h1')
    mountPrivateResolvconf(h1)
    startDHCPclient(h1)
    waitForIP(h1)


def stopNetwork():
    if net is not None:
        info('** Tearing down network\n')
        h1 = net.get('h1')
        unmountPrivateResolvconf(h1)
        stopDHCPclient(h1)
        net.stop()


def test_ping():
    startNetwork()

    # Start the switches and specify a controller
    s1 = net.getNodeByName('s1')
    s2 = net.getNodeByName('s2')

    # Start each controller
    c1 = net.getNodeByName('c1')
    c2 = net.getNodeByName('c2')
    for controller in net.controllers:
        controller.start()
    s1.start([c1])
    s2.start([c2])

    # After switch registered, set protocol to OpenFlow 1.5
    s1.cmd('ovs-vsctl set bridge s1 protocols=OpenFlow15')
    s2.cmd('ovs-vsctl set bridge s2 protocols=OpenFlow15')

    # Send non blocking commands to each host
    h1 = net.getNodeByName('h1')
    h2 = net.getNodeByName('h2')

    h1.setIP('10.0.0.1', prefixLen=24)
    h2.setIP('50.0.0.1', prefixLen=24)

    time.sleep(10)

    # REST API to configure AS1 controller
    c1.addMapping("10.0.0.1", "40.0.0.0/24")
    c1.addMapping("50.0.0.1", "80.0.0.0/16")

    # REST API to configure AS2 controller
    c2.addMapping("10.0.0.1", "40.0.0.0/24")
    c2.addMapping("50.0.0.1", "80.0.0.0/16")

    # End-to-end communication setup as below
    h1.cmd('route add -net 50.0.0.0 netmask 255.255.255.0 dev h1-eth0')
    h2.cmd('route add -net 10.0.0.0 netmask 255.255.255.0 dev h2-eth0')

    time.sleep(3)

    # Query flow rules on each switch and write to log file
    s1.cmd('ovs-ofctl dump-flows s1 -O OpenFlow15 > ' + LOG_PATH + ' s1.log')
    s2.cmd('ovs-ofctl dump-flows s2 -O OpenFlow15 > ' + LOG_PATH + ' s2.log')

    info("** Testing network connectivity\n")
    # packet_loss = net.ping(net.hosts)
    result = h1.cmd('ping -i 1 -c 10 ' + str(h2.IP()))
    sent, received = net._parsePing(result)
    info('Sent:' + str(sent) + ' Received:' + str(received) + '\n')

    stopNetwork()

    assert sent - received == 0


if __name__ == '__main__':
    setLogLevel('info')
    startNetwork()
    CLI(net)
    stopNetwork()
