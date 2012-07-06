#!/usr/bin/python

import sys
import argparse
import json
import httplib
import urllib2

class RestApi(object):

    def __init__(self, server,port):
        self.server = server
        self.port = port

    def get(self, path):
        #ret = self.rest_call(path, {}, 'GET')
        #return ret[2]
        f = urllib2.urlopen('http://'+self.server+':'+str(self.port)+path)
        ret = f.read()
        return json.loads(ret)

    def set(self, path, data):
        ret = self.rest_call(path, data, 'POST')
        return ret[0] == 200

    def remove(self, objtype, data):
        #ret = self.rest_call(data, 'DELETE')
        return ret[0] == 200

    def rest_call(self, path, data, action):
        headers = {
            'Content-type': 'application/json',
            'Accept': 'application/json',
            }
        body = json.dumps(data)
        conn = httplib.HTTPConnection(self.server, self.port)
        conn.request(action, path, body, headers)
        response = conn.getresponse()
        ret = (response.status, response.reason, response.read())
        conn.close()
        print str(ret[2])
        return ret


    
usage_desc =  """
Command descriptions: 

    host [debug]
    link [tunnellinks]
    port <blocked | broadcast>
    memory
    switch
    switchclusters
    counter [DPID] <name>
    switch_stats [DPID] <port | queue | flow | aggregate | desc | table | features | host>
"""

def lookupPath(cmd):
    path = ''

    numargs = len(args.otherargs)

    if args.cmd == 'switch_stats':
        if numargs == 1:
            path = '/wm/core/switch/all/'+args.otherargs[0]+'/json'
        elif numargs == 2:
            path = '/wm/core/switch/'+args.otherargs[0]+'/'+args.otherargs[1]+'/json'
    elif args.cmd == 'switch':
        path = '/wm/core/controller/switches/json'
    elif args.cmd == 'counter':
        if numargs == 1:
            path = '/wm/core/counter/'+args.otherargs[0]+'/json'
        elif numargs == 2:
            path = '/wm/core/counter/'+args.otherargs[0]+'/'+args.otherargs[1]+'/json'
    elif args.cmd == 'memory':
        path = '/wm/core/memory/json'
    elif args.cmd == 'link':
        if numargs == 0:
            path = '/wm/topology/links/json'
        elif numargs == 1:
            path = '/wm/topology/'+args.otherargs[0]+'/json'
    elif args.cmd == 'port' and numargs == 1:
        if args.otherargs[0] == "blocked":
            path = '/wm/topology/blockedports/json'
        elif args.otherargs[0] == "broadcast":
            path = '/wm/topology/broadcastdomainports/json'
    elif args.cmd == 'switchclusters':
        path = '/wm/topology/switchclusters/json'
    elif args.cmd == 'host':
        path = '/wm/device/'
        if len(args.otherargs) == 1 and args.otherargs[0] == 'debug':
            path = '/wm/device/debug'
    else:
        print usage_desc
        path = ''
        exit(0)
    return path

parser = argparse.ArgumentParser(description='process args', usage=usage_desc, epilog='foo bar help')
parser.add_argument('--ip', default='localhost')
parser.add_argument('--port', default=8080)
parser.add_argument('cmd')
parser.add_argument('otherargs', nargs='*')
args = parser.parse_args()

#print args

rest = RestApi(args.ip, args.port)
path = lookupPath(args.cmd)

out = rest.get(path)
print json.dumps(out,sort_keys=True, indent=4)
print "Number of items: " + str(len(out))

