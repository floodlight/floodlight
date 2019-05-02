# Processing A Packet-In Message

## Introduction

In an OpenFlow SDN, when a switch receives a packet on a port, it will try to match the packet to a flow entry in the switch's default flow table. If the switch cannot locate a flow that matches the packet, it will by default* send the packet to the controller as a packet-in for closer inspection and processing. (*OpenFlow 1.0, 1.1, and 1.2 flow tables exhibit this behavior by default. OpenFlow 1.3 and up switches must have a default flow inserted by the controller to accomplish this, which Floodlight does for you.)
As discussed in How to Write a Module, we already know how to receive a packet-in message within a custom module. This tutorial will show how to examine the frame/packet one header and payload at a time.

## Retrieving the Ethernet Frame from the Controller Core

Chances are, any module that listens for packet-in messages will also want to inspect the payload (which is most likely of type Ethernet). Rather than having each individual module perform this deserialization, the Floodlight core will perform this task only once to optimize performance.
As such, there is no need to deserialize the byte[] payload of the packet-in message received within our custom modules. Instead, we can retrieve the already-deserialized Ethernet using the FloodlightContext associated with the packet.

```java
/*
 * Overridden IOFMessageListener's receive() function.
 */
@Override
public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
    case PACKET_IN:
        /* Retrieve the deserialized packet in message */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
  
        /* We will fill in the rest here shortly */
  
        break;
    default:
        break;
    }
    return Command.CONTINUE;
}
```
  
As shown above, the Ethernet frame is retrieved using the FloodlightContext. Under the hood, this Ethernet was created by deserializing the payload of the OFPacketIn message.
After we get the Ethernet object representing our payload, we immediately gain access to all getters and setters of Ethernet. This enables us to e.g. get the source MAC address or VLAN ID.
```java
/*
 * Overridden IOFMessageListener's receive() function.
 */
@Override
public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
    case PACKET_IN:
        /* Retrieve the deserialized packet in message */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
 
        /* Various getters and setters are exposed in Ethernet */
        MacAddress srcMac = eth.getSourceMACAddress();
        VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
 
        /* Still more to come... */
 
        break;
    default:
        break;
    }
    return Command.CONTINUE;
}
```

## Getting Ethernet's Payload (e.g. IPv4 or ARP)

Note that we presently only have access to Ethernet frame headers and not higher-level headers. To gain access to those, we need to first determine the payload of this Ethernet frame. After we do this, we can retrieve the deserialized packet representing the payload. This is commonly an IPv4 or ARP packet.
```java
/*
 * Overridden IOFMessageListener's receive() function.
 */
@Override
public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
    case PACKET_IN:
        /* Retrieve the deserialized packet in message */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
 
        /* Various getters and setters are exposed in Ethernet */
        MacAddress srcMac = eth.getSourceMACAddress();
        VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
 
        /*
         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
         * Note the shallow equality check. EthType caches and reuses instances for valid types.
         */
        if (eth.getEtherType() == EthType.IPv4) {
            /* We got an IPv4 packet; get the payload from Ethernet */
            IPv4 ipv4 = (IPv4) eth.getPayload();
             
            /* More to come here */
        } else if (eth.getEtherType() == EthType.ARP) {
            /* We got an ARP packet; get the payload from Ethernet */
            ARP arp = (ARP) eth.getPayload();
  
            /* More to come here */
  
        } else {
            /* Unhandled ethertype */
        }
        break;
    default:
        break;
    }
    return Command.CONTINUE;
}
```

As shown above, once we determine the payload type, we can cast the IPacket payload to the correct class. All packets defined in net.floodlightcontroller.packet extend BasePacket, which implements IPacket. Since the Floodlight core has already deserialized the entire packet in message, all that is required of us is a cast to the object type that represents the packet. 
Now that we've determined the packet type and retrieved the payload, we have access to the packet header attributes.

```java
/*
 * Overridden IOFMessageListener's receive() function.
 */
@Override
public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
    case PACKET_IN:
        /* Retrieve the deserialized packet in message */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
 
        /* Various getters and setters are exposed in Ethernet */
        MacAddress srcMac = eth.getSourceMACAddress();
        VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
 
        /*
         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
         * Note the shallow equality check. EthType caches and reuses instances for valid types.
         */
        if (eth.getEtherType() == EthType.IPv4) {
            /* We got an IPv4 packet; get the payload from Ethernet */
            IPv4 ipv4 = (IPv4) eth.getPayload();
             
            /* Various getters and setters are exposed in IPv4 */
            byte[] ipOptions = ipv4.getOptions();
            IPv4Address dstIp = ipv4.getDestinationAddress();
             
            /* Still more to come... */
 
        } else if (eth.getEtherType() == EthType.ARP) {
            /* We got an ARP packet; get the payload from Ethernet */
            ARP arp = (ARP) eth.getPayload();
 
            /* Various getters and setters are exposed in ARP */
            boolean gratuitous = arp.isGratuitous();
 
        } else {
            /* Unhandled ethertype */
        }
        break;
    default:
        break;
    }
    return Command.CONTINUE;
}
```

## Getting IPv4's Payload (e.g. TCP or UDP)

Logically, we can next dive into the payload of the IPv4 packet, which is commonly either TCP or UDP.

```java
/*
 * Overridden IOFMessageListener's receive() function.
 */
@Override
public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
    case PACKET_IN:
        /* Retrieve the deserialized packet in message */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
 
        /* Various getters and setters are exposed in Ethernet */
        MacAddress srcMac = eth.getSourceMACAddress();
        VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
 
        /* 
         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
         * Note the shallow equality check. EthType caches and reuses instances for valid types.
         */
        if (eth.getEtherType() == EthType.IPv4) {
            /* We got an IPv4 packet; get the payload from Ethernet */
            IPv4 ipv4 = (IPv4) eth.getPayload();
             
            /* Various getters and setters are exposed in IPv4 */
            byte[] ipOptions = ipv4.getOptions();
            IPv4Address dstIp = ipv4.getDestinationAddress();
             
            /* 
             * Check the IP protocol version of the IPv4 packet's payload.
             */
            if (ipv4.getProtocol() == IpProtocol.TCP) {
                /* We got a TCP packet; get the payload from IPv4 */
                TCP tcp = (TCP) ipv4.getPayload();
  
                /* Various getters and setters are exposed in TCP */
                TransportPort srcPort = tcp.getSourcePort();
                TransportPort dstPort = tcp.getDestinationPort();
                short flags = tcp.getFlags();
                 
                /* Your logic here! */
            } else if (ipv4.getProtocol() == IpProtocol.UDP) {
                /* We got a UDP packet; get the payload from IPv4 */
                UDP udp = (UDP) ipv4.getPayload();
  
                /* Various getters and setters are exposed in UDP */
                TransportPort srcPort = udp.getSourcePort();
                TransportPort dstPort = udp.getDestinationPort();
                 
                /* Your logic here! */
            }
 
        } else if (eth.getEtherType() == EthType.ARP) {
            /* We got an ARP packet; get the payload from Ethernet */
            ARP arp = (ARP) eth.getPayload();
 
            /* Various getters and setters are exposed in ARP */
            boolean gratuitous = arp.isGratuitous();
 
        } else {
            /* Unhandled ethertype */
        }
        break;
    default:
        break;
    }
    return Command.CONTINUE;
}
```

And that's about it! The process is the same for retrieving the payload for other types; IPv4, ARP, TCP, and UDP are only examples in this tutorial.

If your packet/frame's payload is not defined as a class in net.floodlightcontroller.packet, you can optionally add it or work with the returned Data instead. All unknown payloads are exposed as Data IPackets, which have getData() to return a byte[] of the payload.

Note that deserialization is done one header/payload at a time. This means that if you have an Ethernet frame with an IPv4 payload but an unknown-to-Floodlight transport protocol, the packet will be deserialized up to the transport layer. When Floodlight examine's the IPv4 IpProtocol and does not find a pre-defined transport packet, the deserialization process will stop and getPayload() of IPv4 will return a Data object instead. The prior successfully deserialized headers and payloads remain intact and are accessible to modules in their deserialized form.

If you feel there is a packet type that is not defined in net.floodlightcontroller.packet that would be useful to have, please feel free to reach out to the mailing list with your suggestions at floodlight@groups.io.
