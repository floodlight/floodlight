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

    host <MAC>

    link

    memory
    
    switch

    switch_stats [port, queue, flow, aggregate, desc, table, features, host]
    
    per_switch_stats [DPID] [port, queue, flow, aggregate, desc, table, features, host]

    """

def lookupPath(cmd):
    path = ''

    numargs = len(args.otherargs)
        
    if args.cmd == 'switch_stats' and numargs == 1:
        path = '/wm/core/switch/all/'+args.otherargs[0]+'/json'
    elif args.cmd == 'per_switch_stats' and numargs == 2:
        path = '/wm/core/switch/'+args.ortherargs[0]+'/'+args.otherargs[1]+'/json'
    elif args.cmd == 'switch':
        path = '/wm/core/controller/switches/json'
    elif args.cmd == 'counter' and numargs == 1:
        path = '/wm/core/counter/'+args.otherargs[0]+'/json'
    elif args.cmd == 'switch_counter' and numargs == 2:
        path = '/wm/core/counter/'+args.otherargs[0]+'/'+otherargs[1]+'/json'
    elif args.cmd == 'memory':
        path = '/wm/core/memory/json'
    elif args.cmd == 'link':
        path = '/wm/topology/links/json'
    elif args.cmd == 'switchclusters':
        path = '/wm/topology/switchclusters/json'
    elif args.cmd == 'host':
        if len(args.otherargs) == 0:
            args.otherargs.append("all")
        path = '/wm/devicemanager/device/'+args.otherargs[0]+'/json'
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

