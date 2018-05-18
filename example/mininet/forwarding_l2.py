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
This script tests L2 forwarding with linear topology

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


def stopNetwork():
    if net is not None:
        info('** Tearing down network\n')
        net.stop()


if __name__ == '__main__':
    setLogLevel('info')
    startNetworkWithLinearTopo(6)
    CLI(net)
    stopNetwork()

