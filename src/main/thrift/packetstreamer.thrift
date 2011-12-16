#
# Interface definition for packetstreamer service
#

namespace java net.floodlightcontroller.packetstreamer.thrift 
namespace cpp net.floodlightcontroller.packetstreamer
namespace py packetstreamer
namespace php packetstreamer
namespace perl packetstreamer

const string VERSION = "0.1.0"

#
# data structures
#

/**
 * OFMessage type
 **/
enum OFMessageType {
  HELLO = 0,
  ERROR = 1,
  ECHO_REQUEST = 2,
  ECHO_REPLY = 3,
  VENDOR = 4,
  FEATURES_REQUEST = 5,
  FEATURES_REPLY = 6,
  GET_CONFIG_REQUEST = 7,
  GET_CONFIG_REPLY = 8,
  SET_CONFIG = 9,
  PACKET_IN = 10,
  FLOW_REMOVED = 11,
  PORT_STATUS = 12,
  PACKET_OUT = 13,
  FLOW_MOD = 14,
  PORT_MOD = 15,
  STATS_REQUEST = 16,
  STATS_REPLY = 17,
  BARRIER_REQUEST = 18,
  BARRIER_REPLY = 19,
}

/**
 * A struct that defines switch port tuple
 */
struct SwitchPortTuple {
  1: i64 dpid,
  2: i16 port,
}

struct Packet {
  1: OFMessageType messageType,
  2: SwitchPortTuple swPortTuple,
  3: binary data,
}

struct Message {
  1: list<string> sessionIDs,
  2: Packet packet,
}

/**
 * Packetstreamer API
 */
service PacketStreamer {

   /**
    * Synchronous method to get packets for a given sessionid
    */
   list<binary> getPackets(1:string sessionid),

   /**
    * Synchronous method to publish a packet.
    * It ensure the order that the packets are pushed
    */
   i32 pushMessageSync(1:Message packet),

   /** 
    * Asynchronous method to publish a packet.
    * Order is not guaranteed.
    */
   oneway void pushMessageAsync(1:Message packet)

   /** 
    * Terminate a session
    */
   void terminateSession(1:string sessionid)
}
