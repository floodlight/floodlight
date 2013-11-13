#!/usr/bin/env python

import sys
from threading import currentThread
from SocketServer import BaseRequestHandler, TCPServer
from code import InteractiveConsole

_locals = None

class DebugLogger(object):
    def do_print(self, *args):
        for i in args:
            print i,
        print
    info = do_print
    warn = do_print
    debug = do_print
_log = DebugLogger()


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
        currentThread()._thread.setName("debugserver-%s:%d" % client_address)
        _log.debug('Open connection to DebugServer from %s:%d' % client_address)
        BaseRequestHandler.__init__(self, request, client_address, server)

    def handle(self):
        console = DebugConsole(self.request)
        sys.displayhook = console.write_nl
        console.interact('DebugServer')
        self.request.close()

class DebugServer(TCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def handle_error(self, request, client_address):
        _log.debug('Closing connection to DebugServer from %s:%d' % client_address)
        request.close()

def run_server(port=6655, host='', locals=locals()):
    currentThread()._thread.setName("debugserver-main")

    global _locals
    _locals = locals
    if "log" in locals.keys():
        global _log
        _log = locals["log"]

    _log.info("Starting DebugServer on %s:%d" % (host, port))
    server = DebugServer((host, port), DebugServerHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    run_server()
