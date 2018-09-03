#! /usr/bin/python

import os
import sys
import subprocess
import json
import argparse
import io
import time

# manage the load balancer through the REST API
# Syntax:
#   load_balancer --controller {IP:REST_PORT} --addVipPool --id {ID} --protocol {TCP} --address {IP} --lbMethod {WRR} 
#   load_balancer --controller {IP:REST_PORT} --addMemberToPool --id {ID} --poolId {ID} --address {IP} --weight {WEIGHT}
#   load_balancer --controller {IP:REST_PORT} --addMonitorToPool --id {ID} --poolId {ID}
#   load_balancer --controller {IP:REST_PORT} --deleteVipPool --id {ID} 
#   load_balancer --controller {IP:REST_PORT} --deleteMember --id {ID} 
#   load_balancer --controller {IP:REST_PORT} --deleteMonitor --id {ID} 
#   load_balancer --controller {IP:REST_PORT} --updateMemberWeight --id {ID} --weight {WEIGHT}
#   load_balancer --controller {IP:REST_PORT} --setPriorityToMember --id {ID} --poolId {ID}
#   load_balancer --controller {IP:REST_PORT} --setPortStatsPeriod --period {INT} 
#   load_balancer --controller {IP:REST_PORT} --setFlowStatsPeriod --period {INT} 
#   load_balancer --controller {IP:REST_PORT} --listVips 
#   load_balancer --controller {IP:REST_PORT} --listPools 
#   load_balancer --controller {IP:REST_PORT} --listMembers 
#   load_balancer --controller {IP:REST_PORT} --listMonitors 
#   load_balancer --controller {IP:REST_PORT} --enableStatistics 
#   load_balancer --controller {IP:REST_PORT} --disableStatistics
#   load_balancer --controller {IP:REST_PORT} --enableMonitors 
#   load_balancer --controller {IP:REST_PORT} --disableMonitors 
#   load_balancer --controller {IP:REST_PORT} --listSwitchBandwidth --dpid {DPID} --port {PORT}
#   load_balancer --controller {IP:REST_PORT} --listPoolStatistics --poolId {ID}
#   load_balancer --controller {IP:REST_PORT} --listFlowStatistics --dpid {ID} 

parser = argparse.ArgumentParser(description='Load Balancer Management Application')
parser.add_argument('--controller', dest='controllerRestIp', action='store', default='localhost:8080', help='controller IP:RESTport, e.g.,localhost:8080 or A.B.C.D:XY')
parser.add_argument('--id', dest='id', action='store', help='id of the element in question')
parser.add_argument('--dpid', dest='dpid', action='store', default="all", help='dpid of a switch')
parser.add_argument('--port', dest='port', action='store', default="all", help='port of a switch')
parser.add_argument('--poolId', dest='poolId', action='store', help='id of the pool')
parser.add_argument('--vipId', dest='vipId', action='store', help='id of the vip')
parser.add_argument('--address', dest='addr', action='store', default='10.0.0.0', help='address of the element')
parser.add_argument('--protocol', dest='proto', action='store', default='tcp', help='protocol of the element')
parser.add_argument('--lbMethod', dest='lbMethod', action='store', default='RR', help='load balance method:"RR", "WRR" or "Statistics"')
parser.add_argument('--weight', dest='weight', action='store', default="1", help='weight of the member')
parser.add_argument('--period', dest='period', action='store', default="10", help='period for thread execution')
parser.add_argument('--addVipPool', dest='action', action='store_const', const='addVipPool', help='add a vip and pool')
parser.add_argument('--addMemberToPool', dest='action', action='store_const', const="addMemberToPool" , help='add one member to a pool')
parser.add_argument('--addMonitorToPool', dest='action', action='store_const', const='addMonitorToPool', help='add monitor and associate it with a pool')
parser.add_argument('--deleteVipPool', dest='action', action='store_const', const='deleteVipPool',help='delete a vip and the associated pool')
parser.add_argument('--deleteMember', dest='action', action='store_const', const='deleteMember', help='delete a member given an id')
parser.add_argument('--deleteMonitor', dest='action', action='store_const', const='deleteMonitor', help='delete a monitor given an id')
parser.add_argument('--updateMemberWeight', dest='action', action='store_const', const='updateMemberWeight', help='change weight of a member')
parser.add_argument('--setPriorityToMember', dest='action', action='store_const', const='setPriorityToMember', help='change weight of a member to be greater than the others in the same pool')
parser.add_argument('--setPortStatsPeriod', dest='action', action='store_const', const='setPortStatsPeriod', help='change port statistics collection period')
parser.add_argument('--setFlowStatsPeriod', dest='action', action='store_const', const='setFlowStatsPeriod', help='change flow statistics collection period')
parser.add_argument('--setMonitorsPeriod', dest='action', action='store_const', const='setMonitorsPeriod', help='change health monitors period')
parser.add_argument('--listVips', dest='action', action='store_const', const='listVips', help='list current vips')
parser.add_argument('--listPools', dest='action', action='store_const', const='listPools', help='list current pools')
parser.add_argument('--listMembers', dest='action', action='store_const', const='listMembers', help='list current members')
parser.add_argument('--listMonitors', dest='action', action='store_const', const='listMonitors', help='list current monitors')
parser.add_argument('--enableStatistics', dest='action', action='store_const', const='enableStatistics', help='enable statistics collection')
parser.add_argument('--disableStatistics', dest='action', action='store_const', const='disableStatistics', help='disable statistics collection')
parser.add_argument('--enableMonitors', dest='action', action='store_const', const='enableMonitors', help='enable health monitors')
parser.add_argument('--disableMonitors', dest='action', action='store_const', const='disableMonitors', help='disable health monitors')
parser.add_argument('--listSwitchBandwidth', dest='action', action='store_const', const='listSwitchBandwidth', help='list bandwidth of DPID(s) port(s)')
parser.add_argument('--listPoolStatistics', dest='action', action='store_const', const='listPoolStatistics', help='list statistics regarding a LBPool')
parser.add_argument('--listFlowStatistics', dest='action', action='store_const', const='listFlowStatistics', help='list statistics regarding a DPID flows')


args = parser.parse_args()

if args.action=='addVipPool':
	command = "curl -X POST -d '{\"id\": \"%s\", \"name\":\"%s\", \"protocol\":\"%s\",\"address\":\"%s\"}' http://%s/quantum/v1.0/vips/" % (args.controllerRestIp,args.id,"vip" + args.id,args.proto,args.addr,args.controllerRestIp)
	result = os.popen(command).read()
	print result

	command = "curl -X POST -d '{\"id\": \"%s\", \"name\":\"%s\", \"protocol\":\"%s\", \"lb_method\":\"%s\",\"vip_id\":\"%s\"}' http://%s/quantum/v1.0/pools/" % (args.id,"pool" + args.id,args.proto,args.lbMethod,args.id,args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='addMemberToPool':
	command = "curl -X POST -d '{\"id\": \"%s\", \"address\":\"%s\", \"pool_id\":\"%s\",\"weight\":\"%s\"}' http://%s/quantum/v1.0/members/" % (args.id,args.addr,args.poolId,args.weight,args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='addMonitorToPool':
	command = "curl -X POST -d '{\"id\": \"%s\", \"name\":\"%s\",\"pool_id\":\"%s\"}' http://%s/quantum/v1.0/pools/%s/health_monitors" % (args.id,"monitor"+ args.id,args.poolId,args.controllerRestIp,args.poolId)
	result = os.popen(command).read()
	print result

elif args.action=='deleteVipPool':
	command = "curl -X DELETE http://%s/quantum/v1.0/vips/%s" % (args.controllerRestIp,args.id)
	result = os.popen(command).read()
	print result

	command = "curl -X DELETE http://%s/quantum/v1.0/pools/%s" % (args.controllerRestIp,args.id)
	result = os.popen(command).read()
	print result

elif args.action=='deleteMember':
	command = "curl -X DELETE http://%s/quantum/v1.0/members/%s" % (args.controllerRestIp,args.id)
	result = os.popen(command).read()
	print result

elif args.action=='deleteMonitor':
	command = "curl -X DELETE http://%s/quantum/v1.0/health_monitors/%s" % (args.controllerRestIp,args.id)
	result = os.popen(command).read()
	print result

elif args.action=='updateMemberWeight':
	command = "curl -X POST http://%s/quantum/v1.0/members/%s/%s" % (args.controllerRestIp,args.id, args.weight)
	result = os.popen(command).read()
	print result

elif args.action=='setPriorityToMember':
	command = "curl -X POST http://%s/quantum/v1.0/pools/%s/members/%s" % (args.controllerRestIp,args.poolId, args.id)
	result = os.popen(command).read()
	print result

elif args.action=='setPortStatsPeriod':
	command = "curl -X POST http://%s/wm/statistics/config/port/%s" % (args.controllerRestIp,args.period)
	result = os.popen(command).read()
	print result

elif args.action=='setFlowStatsPeriod':
	command = "curl -X POST http://%s/wm/statistics/config/flow/%s" % (args.controllerRestIp,args.period)
	result = os.popen(command).read()
	print result

elif args.action=='setMonitorsPeriod':
	command = "curl -X POST http://%s/quantum/v1.0/health_monitors/monitors/%s" % (args.controllerRestIp,args.period)
	result = os.popen(command).read()
	print result

elif args.action=='listVips':
	command = "curl -X GET http://%s/quantum/v1.0/vips/" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='listPools':
	command = "curl -X GET http://%s/quantum/v1.0/pools/" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='listMembers':
	command = "curl -X GET http://%s/quantum/v1.0/members/" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='listMonitors':
	command = "curl -X GET http://%s/quantum/v1.0/health_monitors/" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='enableStatistics':
	command = "curl -X POST http://%s/wm/statistics/config/enable/json" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='disableStatistics':
	command = "curl -X POST http://%s/wm/statistics/config/disable/json" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='enableMonitors':
	command = "curl -X POST http://%s/quantum/v1.0/health_monitors/enable/" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='disableMonitors':
	command = "curl -X POST http://%s/quantum/v1.0/health_monitors/disable/" % (args.controllerRestIp)
	result = os.popen(command).read()
	print result

elif args.action=='listSwitchBandwidth':
	command = "curl -X GET http://%s/wm/statistics/bandwidth/%s/%s/json" % (args.controllerRestIp,args.dpid,args.port)
	result = os.popen(command).read()
	print result

elif args.action=='listPoolStatistics':
	command = "curl -X GET http://%s/quantum/v1.0/pools/%s/stats" % (args.controllerRestIp,args.poolId)
	result = os.popen(command).read()
	print result

elif args.action=='listFlowStatistics':
	command = "curl -X GET http://%s/wm/statistics/flow/%s/json" % (args.controllerRestIp,args.dpid)
	result = os.popen(command).read()
	print result