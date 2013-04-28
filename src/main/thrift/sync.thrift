#
# Interface definition for sync
#

namespace java org.sdnplatform.sync.thrift
namespace cpp org.sdnplatform.sync.thrift
namespace py sync
namespace php sync
namespace perl sync

const string VERSION = "1.0.0"

#
# data structures
#

struct ClockEntry {
  1: required i16 nodeId,
  2: required i64 version
}

struct VectorClock {
  1: optional list<ClockEntry> versions,
  2: optional i64 timestamp
}

struct VersionedValue {
  1: optional binary value,
  2: required VectorClock version
}

struct SyncError {
  1: i32 errorCode,
  2: string message
}

struct KeyedValues {
  1: required binary key,
  2: required list<VersionedValue> values
}

struct KeyedVersions {
  1: required binary key,
  2: required list<VectorClock> versions
}

struct AsyncMessageHeader {
  1: optional i32 transactionId,
}

enum Scope {
  GLOBAL = 0,
  LOCAL = 1,
  UNSYNCHRONIZED = 2
}

struct Store {
  1: required string storeName,
  2: optional Scope scope,
  3: optional bool persist
}

struct Node {
  1: optional i16 nodeId,
  2: optional i16 domainId,
  3: optional string hostname,
  4: optional i32 port
}

#
# Protocol messages
#

enum MessageType {
  HELLO = 1,
  ERROR = 2,
  ECHO_REQUEST = 3,
  ECHO_REPLY = 4,
  GET_REQUEST = 5,
  GET_RESPONSE = 6,
  PUT_REQUEST = 7,
  PUT_RESPONSE = 8,
  DELETE_REQUEST = 9,
  DELETE_RESPONSE = 10,
  SYNC_VALUE = 11,
  SYNC_VALUE_RESPONSE = 12,
  SYNC_OFFER = 13,
  SYNC_REQUEST = 14,
  FULL_SYNC_REQUEST = 15,
  CURSOR_REQUEST = 16,
  CURSOR_RESPONSE = 17,
  REGISTER_REQUEST = 18,
  REGISTER_RESPONSE = 19,
  CLUSTER_JOIN_REQUEST = 20,
  CLUSTER_JOIN_RESPONSE = 21,
}

enum AuthScheme {
  NO_AUTH = 0,
  CHALLENGE_RESPONSE = 1
}

struct AuthChallengeResponse {
  1: optional string challenge,
  2: optional string response
}

struct HelloMessage {
  1: required AsyncMessageHeader header,
  2: optional i16 nodeId,
  3: optional AuthScheme authScheme,
  4: optional AuthChallengeResponse authChallengeResponse
}

struct ErrorMessage {
  1: required AsyncMessageHeader header,
  2: optional SyncError error,
  3: optional MessageType type
}

struct EchoRequestMessage {
  1: required AsyncMessageHeader header
}

struct EchoReplyMessage {
  1: required AsyncMessageHeader header
}

struct GetRequestMessage {
  1: required AsyncMessageHeader header
  2: required string storeName,
  3: required binary key
}

struct GetResponseMessage {
  1: required AsyncMessageHeader header
  2: list<VersionedValue> values,
  3: optional SyncError error
}

struct PutRequestMessage {
  1: required AsyncMessageHeader header
  2: required string storeName,
  3: required binary key,
  4: optional VersionedValue versionedValue,
  5: optional binary value,
}

struct PutResponseMessage {
  1: required AsyncMessageHeader header
}

struct DeleteRequestMessage {
  1: required AsyncMessageHeader header
  2: required string storeName,
  3: required binary key,
  4: optional VectorClock version
}

struct DeleteResponseMessage {
  1: optional AsyncMessageHeader header,
  2: optional bool deleted
}

struct SyncValueMessage {
  1: required AsyncMessageHeader header,
  2: required Store store,
  3: list<KeyedValues> values,
  4: optional i32 responseTo
}

struct SyncValueResponseMessage {
  1: required AsyncMessageHeader header,
  2: optional i32 count
}

struct SyncOfferMessage {
  1: required AsyncMessageHeader header,
  2: required Store store,
  3: list<KeyedVersions> versions
}

struct SyncRequestMessage {
  1: required AsyncMessageHeader header,
  2: required Store store,
  3: optional list<binary> keys
}

struct FullSyncRequestMessage {
  1: required AsyncMessageHeader header,
}

struct CursorRequestMessage {
  1: required AsyncMessageHeader header,
  2: optional string storeName,
  3: optional i32 cursorId,
  4: optional bool close
}

struct CursorResponseMessage {
  1: required AsyncMessageHeader header,
  2: required i32 cursorId,
  3: list<KeyedValues> values
}

struct RegisterRequestMessage {
  1: required AsyncMessageHeader header,
  2: required Store store
}

struct RegisterResponseMessage {
  1: required AsyncMessageHeader header,
}

struct ClusterJoinRequestMessage {
  1: required AsyncMessageHeader header,
  2: required Node node
}

struct ClusterJoinResponseMessage {
  1: required AsyncMessageHeader header,
  2: optional i16 newNodeId,
  3: optional list<KeyedValues> nodeStore
}

#
# Message wrapper
#

struct SyncMessage {
  1: required MessageType type,
  2: optional HelloMessage hello,
  3: optional ErrorMessage error,
  4: optional EchoRequestMessage echoRequest,
  5: optional EchoReplyMessage echoReply,
  6: optional GetRequestMessage getRequest,
  7: optional GetResponseMessage getResponse,
  8: optional PutRequestMessage putRequest,
  9: optional PutResponseMessage putResponse,
  10: optional DeleteRequestMessage deleteRequest,
  11: optional DeleteResponseMessage deleteResponse,
  12: optional SyncValueMessage syncValue,
  13: optional SyncValueResponseMessage syncValueResponse,
  14: optional SyncOfferMessage syncOffer,
  15: optional SyncRequestMessage syncRequest,
  16: optional FullSyncRequestMessage fullSyncRequest,
  17: optional CursorRequestMessage cursorRequest,
  18: optional CursorResponseMessage cursorResponse,
  19: optional RegisterRequestMessage registerRequest,
  20: optional RegisterResponseMessage registerResponse,
  21: optional ClusterJoinRequestMessage clusterJoinRequest,
  22: optional ClusterJoinResponseMessage clusterJoinResponse,
}