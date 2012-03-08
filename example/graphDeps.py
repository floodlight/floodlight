#!/usr/bin/python

import urllib2
import json
import sys


def simple_json_get(url):
    return json.loads(urllib2.urlopen(url).read())


def shorten(s):
    return  s.replace('net.floodlightcontroller','n.f'
            ).replace('com.bigswitch','c.b')

def usage(s):
    sys.stderr.write("Usage:\ngrahDeps.py hostname [port]\n%s" % s)
    sys.stderr.write("\n\n\n\n    writes data to 'hostname.dot' for use with graphviz\n")
    sys.exit(1)


if __name__ == '__main__':

    host='localhost'
    port=8080

    if len(sys.argv) == 1 or sys.argv[1] == '-h' or sys.argv[1] == '--help':
        usage("need to specify hostname")

    host = sys.argv[1]
    if len(sys.argv) > 2:
        port = int(sys.argv[2])

    sys.stderr.write("Connecting to %s:%d ..." % (host,port))
    URL="http://%s:%d/wm/core/module/loaded/json" % (host,port)

    deps = simple_json_get(URL)
    serviceMap = {}
    nodeMap = {}
    nodeCount = 0

    sys.stderr.write("Writing to %s.dot ..." % (host))
    f = open("%s.dot" % host, 'w')

    f.write( "digraph Deps {\n")

    for mod, info in deps.iteritems():
        # sys.stderr.write("Discovered module %s\n" % mod)
        nodeMap[mod] = "n%d" % nodeCount
        nodeCount += 1
        label = shorten(mod) + "\\n"
        for service, serviceImpl in info['provides'].iteritems():
            # sys.stderr.write("     Discovered service %s implemented with %s\n" % (service,serviceImpl))
            label += "\\nService=%s" % shorten(service)
            serviceMap[serviceImpl] = mod
        f.write("     %s [ label=\"%s\", color=\"blue\"];\n" % (nodeMap[mod], label))

    f.write("\n")      # for readability

    for mod, info in deps.iteritems():
        for dep, serviceImpl in info['depends'].iteritems():
            f.write("     %s -> %s [ label=\"%s\"];\n" % (
                    nodeMap[mod],
                    shorten(nodeMap[serviceMap[serviceImpl]]),
                    shorten(dep)))
        

    f.write("}\n")
    f.close();
    sys.stderr.write("Now type\ndot -Tpdf -o %s.pdf %s.dot\n" % (
        host, host))
        
