#!/usr/bin/python
import json

import httplib
import os
import subprocess
import time

HOME_FOLDER = os.getenv('HOME')

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


def addStaticAddressToDHCPInstance1(name):
    data = {
        "staticaddresses": [
            {
                "mac" : "1a:64:13:ac:f0:d1",
                "ip" : "10.0.0.111"
            }
        ]
    }
    ret = rest_call('wm/dhcp/instance/' + name, data, 'POST')
    return ret


def updateDefaultGateway(name):
    data = {
        "name"         : name,
        "router-ip"    : "10.0.0.10"
    }
    ret = rest_call('wm/dhcp/instance/' + name, data, 'POST')
    return ret

if __name__ == '__main__':
    # ret = addStaticAddressToDHCPInstance1('mininet-dhcp-1')
    # print(ret)

    ret = updateDefaultGateway('mininet-dhcp-1')
    print(ret)


