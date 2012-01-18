#!/usr/bin/env python

import sys
from SocketServer import BaseRequestHandler, TCPServer
from code import InteractiveConsole

_locals = None

class DebugConsole(InteractiveConsole):
    def __init__(self, request):
        self.request = request
        InteractiveConsole.__init__(self, _locals)

    def raw_input(self, prompt):
        self.request.send(prompt)
        data = self.request.recv(10000).rstrip()
        if len(data) == 1 and ord(data[0]) == 4:
            sys.exit()
        return data

    def write(self, data):
        self.request.send(str(data))

    def write_nl(self, data):
        self.write(str(data)+"\r\n")

class DebugServerHandler(BaseRequestHandler):
    def __init__(self, request, client_address, server):
        print 'Open connection to DebugServer from: %s' % str(client_address)
        BaseRequestHandler.__init__(self, request, client_address, server)

    def handle(self):
        console = DebugConsole(self.request)
        sys.displayhook = console.write_nl
        console.interact('DebugServer')
        self.request.close()

class DebugServer(TCPServer):
    def handle_error(self, request, client_address):
        print 'Closing connection to DebugServer from: %s' % str(client_address)
        request.close()

def run_server(port=6655, host='0.0.0.0', locals=locals()):
    global _locals
    _locals = locals

    print "Starting DebugServer on port %d" % port
    server = DebugServer(('', port), DebugServerHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    run_server()
