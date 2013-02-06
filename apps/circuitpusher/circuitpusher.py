#! /usr/bin/python
"""
Copyright 2013, Big Switch Networks, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain
a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations
under the License.

circuitpusher utilizes floodlight rest APIs to create a bidirectional circuit, 
i.e., permanent flow entry, on all switches in route between two devices based 
on IP addresses with specified priority.
 
Notes:
 1. The circuit pusher currently only creates circuit with two IP end points 
 2. Prior to sending restAPI requests to the circuit pusher, the specified end
    points must already been known to the controller (i.e., already have sent
    packets on the network, easy way to assure this is to do a ping (to any
    target) from the two hosts.
 3. The current supported command syntax format is:
    a) circuitpusher.py --controller={IP}:{rest port} --type ip --src {IP} --dst {IP} --add --name {circuit-name}
 
       adds a new circuit between src and dst devices Currently ip circuit is supported. ARP is automatically supported.
    
       Currently a simple circuit record storage is provided in a text file circuits.json in the working directory.
       The file is not protected and does not clean itself between controller restarts.  The file is needed for correct operation
       and the user should make sure deleting the file when floodlight controller is restarted.

    b) circuitpusher.py --controller={IP}:{rest port} --delete --name {circuit-name}

       deletes a created circuit (as recorded in circuits.json) using the previously given name

@author kcwang
"""

import os
import sys
import subprocess
import json
import argparse
import io
import time

# parse circuit options.  Currently supports add and delete actions.
# Syntax:
#   circuitpusher --controller {IP:REST_PORT} --add --name {CIRCUIT_NAME} --type ip --src {IP} --dst {IP} 
#   circuitpusher --controller {IP:REST_PORT} --delete --name {CIRCUIT_NAME}

parser = argparse.ArgumentParser(description='Circuit Pusher')
parser.add_argument('--controller', dest='controllerRestIp', action='store', default='localhost:8080', help='controller IP:RESTport, e.g., localhost:8080 or A.B.C.D:8080')
parser.add_argument('--add', dest='action', action='store_const', const='add', default='add', help='action: add, delete')
parser.add_argument('--delete', dest='action', action='store_const', const='delete', default='add', help='action: add, delete')
parser.add_argument('--type', dest='type', action='store', default='ip', help='valid types: ip')
parser.add_argument('--src', dest='srcAddress', action='store', default='0.0.0.0', help='source address: if type=ip, A.B.C.D')
parser.add_argument('--dst', dest='dstAddress', action='store', default='0.0.0.0', help='destination address: if type=ip, A.B.C.D')
parser.add_argument('--name', dest='circuitName', action='store', default='circuit-1', help='name for circuit, e.g., circuit-1')

args = parser.parse_args()
print args

controllerRestIp = args.controllerRestIp

# first check if a local file exists, which needs to be updated after add/delete
if os.path.exists('./circuits.json'):
    circuitDb = open('./circuits.json','r')
    lines = circuitDb.readlines()
    circuitDb.close()
else:
    lines={}

if args.action=='add':

    circuitDb = open('./circuits.json','a')
    
    for line in lines:
        data = json.loads(line)
        if data['name']==(args.circuitName):
            print "Circuit %s exists already. Use new name to create." % args.circuitName
            sys.exit()
        else:
            circuitExists = False
    
    # retrieve source and destination device attachment points
    # using DeviceManager rest API 
    
    command = "curl -s http://%s/wm/device/?ipv4=%s" % (args.controllerRestIp, args.srcAddress)
    result = os.popen(command).read()
    parsedResult = json.loads(result)
    print command+"\n"
    sourceSwitch = parsedResult[0]['attachmentPoint'][0]['switchDPID']
    sourcePort = parsedResult[0]['attachmentPoint'][0]['port']
    
    command = "curl -s http://%s/wm/device/?ipv4=%s" % (args.controllerRestIp, args.dstAddress)
    result = os.popen(command).read()
    parsedResult = json.loads(result)
    print command+"\n"
    destSwitch = parsedResult[0]['attachmentPoint'][0]['switchDPID']
    destPort = parsedResult[0]['attachmentPoint'][0]['port']
    
    print "Creating circuit:"
    print "from source device at switch %s port %s" % (sourceSwitch,sourcePort)
    print "to destination device at switch %s port %s"% (destSwitch,destPort)
    
    # retrieving route from source to destination
    # using Routing rest API
    
    command = "curl -s http://%s/wm/topology/route/%s/%s/%s/%s/json" % (controllerRestIp, sourceSwitch, sourcePort, destSwitch, destPort)
    
    result = os.popen(command).read()
    parsedResult = json.loads(result)

    print command+"\n"
    print result+"\n"

    for i in range(len(parsedResult)):
        if i % 2 == 0:
            ap1Dpid = parsedResult[i]['switch']
            ap1Port = parsedResult[i]['port']
            print ap1Dpid, ap1Port
            
        else:
            ap2Dpid = parsedResult[i]['switch']
            ap2Port = parsedResult[i]['port']
            print ap2Dpid, ap2Port
            
            # send one flow mod per pair of APs in route
            # using StaticFlowPusher rest API

            # IMPORTANT NOTE: current Floodlight StaticflowEntryPusher
            # assumes all flow entries to have unique name across all switches
            # this will most possibly be relaxed later, but for now we
            # encode each flow entry's name with both switch dpid, user
            # specified name, and flow type (f: forward, r: reverse, farp/rarp: arp)

            command = "curl -s -d '{\"switch\": \"%s\", \"name\":\"%s\", \"src-ip\":\"%s\", \"dst-ip\":\"%s\", \"ether-type\":\"%s\", \"cookie\":\"0\", \"priority\":\"32768\", \"ingress-port\":\"%s\",\"active\":\"true\", \"actions\":\"output=%s\"}' http://%s/wm/staticflowentrypusher/json" % (ap1Dpid, ap1Dpid+"."+args.circuitName+".f", args.srcAddress, args.dstAddress, "0x800", ap1Port, ap2Port, controllerRestIp)
            result = os.popen(command).read()
            print command

            command = "curl -s -d '{\"switch\": \"%s\", \"name\":\"%s\", \"ether-type\":\"%s\", \"cookie\":\"0\", \"priority\":\"32768\", \"ingress-port\":\"%s\",\"active\":\"true\", \"actions\":\"output=%s\"}' http://%s/wm/staticflowentrypusher/json" % (ap1Dpid, ap1Dpid+"."+args.circuitName+".farp", "0x806", ap1Port, ap2Port, controllerRestIp)
            result = os.popen(command).read()
            print command


            command = "curl -s -d '{\"switch\": \"%s\", \"name\":\"%s\", \"src-ip\":\"%s\", \"dst-ip\":\"%s\", \"ether-type\":\"%s\", \"cookie\":\"0\", \"priority\":\"32768\", \"ingress-port\":\"%s\",\"active\":\"true\", \"actions\":\"output=%s\"}' http://%s/wm/staticflowentrypusher/json" % (ap1Dpid, ap1Dpid+"."+args.circuitName+".r", args.dstAddress, args.srcAddress, "0x800", ap2Port, ap1Port, controllerRestIp)
            result = os.popen(command).read()
            print command

            command = "curl -s -d '{\"switch\": \"%s\", \"name\":\"%s\", \"ether-type\":\"%s\", \"cookie\":\"0\", \"priority\":\"32768\", \"ingress-port\":\"%s\",\"active\":\"true\", \"actions\":\"output=%s\"}' http://%s/wm/staticflowentrypusher/json" % (ap1Dpid, ap1Dpid+"."+args.circuitName+".rarp", "0x806", ap2Port, ap1Port, controllerRestIp)
            result = os.popen(command).read()
            print command
            
            # store created circuit attributes in local ./circuits.json
            datetime = time.asctime()
            circuitParams = {'name':args.circuitName, 'Dpid':ap1Dpid, 'inPort':ap1Port, 'outPort':ap2Port, 'datetime':datetime}
            str = json.dumps(circuitParams)
            circuitDb.write(str+"\n")

        # confirm successful circuit creation
        # using controller rest API
            
        command="curl -s http://%s/wm/core/switch/all/flow/json| python -mjson.tool" % (controllerRestIp)
        result = os.popen(command).read()
        print command + "\n" + result

elif args.action=='delete':
    
    circuitDb = open('./circuits.json','w')

    # removing previously created flow from switches
    # using StaticFlowPusher rest API       
    # currently, circuitpusher records created circuits in local file ./circuits.db 
    # with circuit name and list of switches                                  

    circuitExists = False

    for line in lines:
        data = json.loads(line)
        if data['name']==(args.circuitName):
            circuitExists = True

            sw = data['Dpid']
            print data, sw

            command = "curl -X DELETE -d '{\"name\":\"%s\", \"switch\":\"%s\"}' http://%s/wm/staticflowentrypusher/json" % (sw+"."+args.circuitName+".f", sw, controllerRestIp)
            result = os.popen(command).read()
            print command, result

            command = "curl -X DELETE -d '{\"name\":\"%s\", \"switch\":\"%s\"}' http://%s/wm/staticflowentrypusher/json" % (sw+"."+args.circuitName+".farp", sw, controllerRestIp)
            result = os.popen(command).read()
            print command, result

            command = "curl -X DELETE -d '{\"name\":\"%s\", \"switch\":\"%s\"}' http://%s/wm/staticflowentrypusher/json" % (sw+"."+args.circuitName+".r", sw, controllerRestIp)
            result = os.popen(command).read()
            print command, result

            command = "curl -X DELETE -d '{\"name\":\"%s\", \"switch\":\"%s\"}' http://%s/wm/staticflowentrypusher/json" % (sw+"."+args.circuitName+".rarp", sw, controllerRestIp)
            result = os.popen(command).read()
            print command, result            
            
        else:
            circuitDb.write(line)

    circuitDb.close()

    if not circuitExists:
        print "specified circuit does not exist"
        sys.exit()

