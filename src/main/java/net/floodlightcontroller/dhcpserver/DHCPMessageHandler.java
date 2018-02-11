package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.*;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.floodlightcontroller.packet.DHCP.DHCPOptionCode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 2/4/18
 *
 * This class handles DHCP message based on DHCP spec.
 *
 */
public class DHCPMessageHandler {
    protected static Logger log = LoggerFactory.getLogger(DHCPMessageHandler.class);

    public OFPacketOut handleDHCPDiscover(@Nonnull IOFSwitch sw, @Nonnull OFPort inPort, @Nonnull DHCPInstance instance,
                                          @Nonnull IPv4Address clientIP, @Nonnull DHCP payload) {
        /**  DHCP Discover Message
         * -- UDP src port = 68
         * -- UDP dst port = 67
         * -- IP src addr = 0.0.0.0
         * -- IP dst addr = 255.255.255.255
         * -- Opcode = 0x01
         * -- XID = transactionX
         * --
         * -- All addresses blank:
         * --	ciaddr (client IP)
         * --	yiaddr (your IP)
         * --	siaddr (DHCPServer IP)
         * --	giaddr (GW IP)
         * --   chaddr = Client's MAC
         * --
         * -- Options:
         * --	Option 53 = DHCP Discover
         * --	Option 50 = possible IP request
         * --	Option 55 = parameter request list
         * --		(1)  SN Mask
         * --		(3)  Router
         * --		(15) Domain Name
         * --		(6)  DNS
         **/
        int xid = payload.getTransactionId();
        IPv4Address yiaddr = payload.getYourIPAddress();
        IPv4Address giaddr = payload.getGatewayIPAddress();    // Will have GW IP if a relay agent was used
        MacAddress chaddr = payload.getClientHardwareAddress();
        List<Byte> requestOrder = new ArrayList<>();
        IPv4Address requestIP = null;

        for (DHCPOption option : payload.getOptions()) {
            if (option.getCode() == DHCPOptionCode.OptionCode_RequestedIP.getCode()) {
                requestIP = IPv4Address.of(option.getData());
                log.debug("Handling Discover Message: got requested IP");
            } else if (option.getCode() == DHCPOptionCode.OptionCode_RequestedParameters.getCode()) {
                requestOrder = getRequestedParameters(payload, false);
                log.debug("Handling Discover Message: got requested param list");
            }
        }

        DHCPPool dhcpPool = instance.getDHCPPool();
        long leaseTime = instance.getLeaseTimeSec();
        IPv4Address ipLease = null;
        if (requestIP == null) {
            ipLease = dhcpPool.assignLeaseToClient(chaddr, leaseTime).get();
        } else {
            ipLease = dhcpPool.assignLeaseToClientWithRequestIP(requestIP,
                    chaddr, leaseTime).orElse(dhcpPool.assignLeaseToClient(chaddr, leaseTime).get());
        }
        yiaddr = ipLease;

        DHCP dhcpOffer = buildDHCPOfferMessage(instance, chaddr, yiaddr, giaddr, xid, requestOrder);

        return buildDHCPOfferPacketOut(instance, sw, inPort, clientIP, dhcpOffer);

    }

    public List<Byte> getRequestedParameters(DHCP dhcpPayload, boolean isInform) {
        ArrayList<Byte> requestOrder = new ArrayList<>();
        byte[] requests = dhcpPayload.getOption(DHCPOptionCode.OptionCode_RequestedParameters).getData();
        boolean requestedLeaseTime = false;
        boolean requestedRebindTime = false;
        boolean requestedRenewTime = false;

        for (byte specificRequest : requests) {
            if (specificRequest == DHCPOptionCode.OptionCode_SubnetMask.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_SubnetMask.getCode());

            } else if (specificRequest == DHCPOptionCode.OptionCode_Router.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_Router.getCode());

            } else if (specificRequest == DHCPOptionCode.OptionCode_DomainName.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_DomainName.getCode());

            } else if (specificRequest == DHCPOptionCode.OptionCode_DNS.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_DNS.getCode());

            } else if (specificRequest == DHCPOptionCode.OptionCode_LeaseTime.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_LeaseTime.getCode());
                requestedLeaseTime = true;

            } else if (specificRequest == DHCPOptionCode.OptionCode_DHCPServerID.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_DHCPServerID.getCode());

            } else if (specificRequest == DHCPOptionCode.OptionCode_Broadcast_IP.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_Broadcast_IP.getCode());

            } else if (specificRequest == DHCPOptionCode.OptionCode_NTP_IP.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_NTP_IP.getCode());

            } else if (specificRequest == DHCPOptionCode.OPtionCode_RebindingTime.getCode()) {
                requestOrder.add(DHCPOptionCode.OPtionCode_RebindingTime.getCode());
                requestedRebindTime = true;

            } else if (specificRequest == DHCPOptionCode.OptionCode_RenewalTime.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_RenewalTime.getCode());
                requestedRenewTime = true;

            } else if (specificRequest == DHCPOptionCode.OptionCode_IPForwarding.getCode()) {
                requestOrder.add(DHCPOptionCode.OptionCode_IPForwarding.getCode());
                log.debug("requested IP FORWARDING");

            } else {
                log.debug("Requested option 0x" + Byte.toString(specificRequest) + " not available");

            }
        }

        // We need to add these in regardless if the request list includes them
        if (!isInform) {
            if (!requestedLeaseTime) {
                requestOrder.add(DHCPOptionCode.OptionCode_LeaseTime.getCode());
                log.debug("added option LEASE TIME");
            }
            if (!requestedRenewTime) {
                requestOrder.add(DHCPOptionCode.OptionCode_RenewalTime.getCode());
                log.debug("added option RENEWAL TIME");
            }
            if (!requestedRebindTime) {
                requestOrder.add(DHCPOptionCode.OPtionCode_RebindingTime.getCode());
                log.debug("added option REBIND TIME");
            }
        }

        return requestOrder;
    }

    private DHCP buildDHCPOfferMessage(DHCPInstance instance, MacAddress chaddr, IPv4Address yiaddr, IPv4Address giaddr, int xid, List<Byte> requestOrder) {
        DHCP dhcpOffer = new DHCP()
                .setOpCode(DHCP.DHCPOpCode.OpCode_Reply.getCode())
                .setHardwareType((byte) 1)
                .setHardwareAddressLength((byte) 6)
                .setHops((byte) 0)
                .setTransactionId(xid)
                .setSeconds((short) 0)
                .setFlags((short) 0)
                .setClientIPAddress(IPv4Address.FULL_MASK)
                .setYourIPAddress(yiaddr)
                .setServerIPAddress(instance.getServerIP())
                .setGatewayIPAddress(giaddr)
                .setClientHardwareAddress(chaddr);

        List<DHCPOption> dhcpOfferOptions = new ArrayList<>();

        DHCPOption newOption;
        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_MessageType.getCode())
                .setData(new byte[]{DHCP.DHCPMessageType.DISCOVER.getCode()})
                .setLength((byte) 1);
        dhcpOfferOptions.add(newOption);

        for (Byte specificRequest : requestOrder) {
            newOption = new DHCPOption();
            if (specificRequest == DHCPOptionCode.OptionCode_SubnetMask.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_SubnetMask.getCode())
                        .setData(instance.getSubnetMask().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Router.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Router.getCode())
                        .setData(instance.getRouterIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DomainName.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DomainName.getCode())
                        .setData(instance.getDomainName().getBytes())
                        .setLength((byte) instance.getDomainName().getBytes().length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DNS.getCode()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getDNSServers()); // Convert List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_DNS.getCode())
                        .setData(byteArray)
                        .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Broadcast_IP.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Broadcast_IP.getCode())
                        .setData(instance.getBroadcastIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DHCPServerID.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DHCPServerID.getCode())
                        .setData(instance.getServerIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_LeaseTime.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_LeaseTime.getCode())
                        .setData(DHCPServerUtils.intToBytes(instance.getLeaseTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_NTP_IP.getCode()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getNtpServers()); // Convert List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_NTP_IP.getCode())
                        .setData(byteArray)
                        .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OPtionCode_RebindingTime.getCode()) {
                newOption.setCode(DHCPOptionCode.OPtionCode_RebindingTime.getCode())
                        .setData(DHCPServerUtils.intToBytes(instance.getRebindTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_RenewalTime.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_RenewalTime.getCode())
                        .setData(DHCPServerUtils.intToBytes(instance.getRenewalTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_IPForwarding.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_IPForwarding.getCode())
                        .setData(DHCPServerUtils.intToBytes(instance.getIpforwarding() ? 1 : 0))
                        .setLength((byte) 4);

            } else {
                log.debug("Setting specific request for OFFER failed");

            }

            dhcpOfferOptions.add(newOption);
        }

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_END.getCode())
                .setLength((byte) 0);
        dhcpOfferOptions.add(newOption);

        dhcpOffer.setOptions(dhcpOfferOptions);
        return dhcpOffer;

    }

    /**
     * DHCP Offer Message
     * -- UDP src port = 67
     * -- UDP dst port = 68
     * -- IP src addr = DHCP DHCPServer's IP
     * -- IP dst addr = 255.255.255.255
     * -- Opcode = 0x02
     * -- XID = transactionX
     * -- ciaddr = blank
     * -- yiaddr = offer IP
     * -- siaddr = DHCP DHCPServer IP
     * -- giaddr = blank
     * -- chaddr = Client's MAC
     * --
     * -- Options:
     * --	Option 53 = DHCP Offer
     * --	Option 1 = SN Mask IP
     * --	Option 3 = Router IP
     * --	Option 51 = Lease time (s)
     * --	Option 54 = DHCP DHCPServer IP
     * --	Option 6 = DNS servers
     **/
    public OFPacketOut buildDHCPOfferPacketOut(DHCPInstance instance, IOFSwitch sw, OFPort inPort, IPv4Address clientIPAddress, DHCP dhcpOfferPacket) {

        if (clientIPAddress.equals(IPv4Address.NONE)) {
            clientIPAddress = IPv4Address.NO_MASK;      // Broadcast IP
        }

        IPacket DHCPOfferReplyEthernet = new Ethernet()
                .setSourceMACAddress(instance.getServerMac())
                .setDestinationMACAddress(dhcpOfferPacket.getClientHardwareAddress())
                .setEtherType(EthType.IPv4)
                .setPayload(
                        new IPv4()
                                .setTtl((byte) 64)
                                .setSourceAddress(instance.getServerIP())
                                .setDestinationAddress(clientIPAddress)
                                .setProtocol(IpProtocol.UDP)
                                .setPayload(
                                        new UDP()
                                                .setDestinationPort(UDP.DHCP_CLIENT_PORT)
                                                .setSourcePort(UDP.DHCP_SERVER_PORT)
                                                .setPayload(dhcpOfferPacket)
                                )

                );

        byte[] serializedPacket = DHCPOfferReplyEthernet.serialize();

        OFPacketOut.Builder packetOutBuilder = sw.getOFFactory().buildPacketOut();
        List<OFAction> actions = new ArrayList<>();
        actions.add(sw.getOFFactory().actions().output(inPort, 0xffFFffFF));

        packetOutBuilder
                .setBufferId(OFBufferId.NO_BUFFER)
                .setInPort(OFPort.ANY)
                .setActions(actions)
                .setData(serializedPacket);

        return packetOutBuilder.build();

    }

    /**
     * DHCP Request Message
     * <p>
     * <p>
     * This function handles DHCP request message(ACK or NAK) based on different host client status, while
     * client status can be determined by "Request IP" and "Server IP".
     * <p>
     * Client Host Possible State
     * - "init_reboot" : client sends "Request IP" to DHCP server and expected to get the same lease IP as last time
     * <p>
     * - "selecting"   : client sends "Request IP" to DHCP server
     * <p>
     * <p>
     * <p>
     * <p>
     * 1) Different "Server IP" -- Client's DHCP Request is not target on this DHCP server (i.e. client accept DHCP
     * Offer from another DHCP server and send DHCP Request to that DHCP server) -- should ignore this DHCP Request
     * and cancel the record in DHCP binding
     * <p>
     * <p>
     * 2)
     * Client is on "init_reboot" status, check Request IP matching the IP
     * <p>
     * <p>
     * 1)
     * 2) Client's DHCP Request is target on this DHCP server
     * a) but client wants a different IP than the IP we have on-file - lease Request IP to client(if that IP available)
     * b) retrieve other DHCP Request message parameters
     * <p>
     * <p>
     * DHCP Request Handling
     * 1)
     *
     * @param sw
     * @param inPort
     * @param instance
     * @param srcAddr
     * @param payload
     * @return
     */
    public OFPacketOut handleDHCPRequest(@Nonnull IOFSwitch sw, @Nonnull OFPort inPort, @Nonnull DHCPInstance instance,
                                         @Nonnull IPv4Address srcAddr, @Nonnull IPv4Address dstAddr, @Nonnull DHCP payload) {

        /** DHCP Request Message
         * -- UDP src port = 68
         * -- UDP dst port = 67
         * -- IP src addr = 0.0.0.0
         * -- IP dst addr = 255.255.255.255
         * -- Opcode = 0x01
         * -- XID = transactionX
         * -- ciaddr = blank
         * -- yiaddr = blank
         * -- siaddr = DHCP Server IP
         * -- giaddr = GW IP
         * -- chaddr = Client's MAC
         * --
         * -- Options:
         * --	Option 53 = DHCP Request
         * --	Option 50 = IP requested
         * --	Option 54 = DHCP Server Identifier
         **/
        int xid = payload.getTransactionId();
        IPv4Address ciaddr = payload.getClientIPAddress();
        IPv4Address yiaddr = payload.getYourIPAddress();
        IPv4Address giaddr = payload.getGatewayIPAddress();    // Will have GW IP if a relay agent was used
        MacAddress chaddr = payload.getClientHardwareAddress();
        List<Byte> requestOrder = new ArrayList<>();
        IPv4Address requestIP = null;
        IPv4Address serverID = null;

        DHCPPool dhcpPool = instance.getDHCPPool();
        for (DHCPOption option : payload.getOptions()) {
            if (option.getCode() == DHCPOptionCode.OptionCode_DHCPServerID.getCode()) {
                serverID = IPv4Address.of(option.getData());
            } else if (option.getCode() == DHCPOptionCode.OptionCode_RequestedIP.getCode()) {
                requestIP = IPv4Address.of(option.getData());
            } else if (option.getCode() == DHCPOptionCode.OptionCode_RequestedParameters.getCode()) {
                requestOrder = getRequestedParameters(payload, false);
            }
        }

        OFPacketOut packetOut = null;
        switch (determineClientState(requestIP, serverID, dstAddr)) {
            case INIT_REBOOT:
                packetOut = handleInitReboot(instance, sw, inPort, requestIP, giaddr, chaddr, yiaddr,
                                            dstAddr, xid, requestOrder);
                break;

            case SELECTING:
                packetOut = handleSelecting();
                break;

            case RENEWING:
                packetOut = handleRenewing();
                break;

            case REBINDING:
                packetOut = handleRebinding();
                break;
        }

        return packetOut;

    }

    private IDHCPService.ClientState determineClientState(@Nullable IPv4Address requstIP, @Nullable IPv4Address serverID,
                                                          IPv4Address dstAddr) {

        if (requstIP != null && serverID == null) {
            return IDHCPService.ClientState.INIT_REBOOT;
        } else if (requstIP != null && serverID != null) {
            return IDHCPService.ClientState.SELECTING;
        } else if (requstIP == null && serverID == null && dstAddr != IPv4Address.NO_MASK) {
            return IDHCPService.ClientState.RENEWING;
        } else if (requstIP == null && serverID == null && dstAddr == IPv4Address.NO_MASK) {
            return IDHCPService.ClientState.REBINDING;
        } else {
            return IDHCPService.ClientState.UNKNOWN; // This shouldn't happen
        }
    }

    /**
     * This function handles the "Init_Reboot" state of the client
     * <p>
     * DHCP Server should send DHCPNAK message to client if "Request IP" is incorrect, or is on the wrong network
     *
     * @param requestIP
     * @param giaddr
     * @return
     */
    private OFPacketOut handleInitReboot(DHCPInstance instance, IOFSwitch sw, OFPort inPort, IPv4Address requestIP,
                                         IPv4Address giaddr, MacAddress chaddr, IPv4Address yiaddr,
                                         IPv4Address dstIPAddr, int xid, List<Byte> requestOrder) {
        boolean sendACK = true;
        if (!giaddr.equals(instance.getRouterIP())) { // TODO: maybe more complex than this
            sendACK = false;
        } else if (requestIP.equals(instance.getDHCPPool().getLeaseIP(chaddr))) {
            sendACK = false;
        } else {
            sendACK = true;
        }

        if (sendACK) {
            return createDHCPAck(instance, sw, inPort, chaddr, dstIPAddr, yiaddr, giaddr, xid, requestOrder);
        } else {
            return createDHCPNak(instance, sw, inPort, chaddr, giaddr, xid);
        }

    }


    public OFPacketOut createDHCPAck(DHCPInstance instance, IOFSwitch sw, OFPort inPort, MacAddress chaddr, IPv4Address dstIPAddr,
                                     IPv4Address yiaddr, IPv4Address giaddr, int xid, List<Byte> requestOrder) {
        /** DHCP ACK Message
         * -- UDP src port = 67
         * -- UDP dst port = 68
         * -- IP src addr = DHCP DHCPServer's IP
         * -- IP dst addr = 255.255.255.255
         * -- Opcode = 0x02
         * -- XID = transactionX
         * -- ciaddr = blank
         * -- yiaddr = offer IP
         * -- siaddr = DHCP DHCPServer IP
         * -- giaddr = blank
         * -- chaddr = Client's MAC
         * --
         * -- Options:
         * --	Option 53 = DHCP ACK
         * --	Option 1  = SN Mask IP
         * --	Option 3  = Router IP
         * --	Option 51 = Lease time (s)
         * --	Option 54 = DHCP DHCPServer IP
         * --	Option 6  = DNS servers
         **/
        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        pob.setBufferId(OFBufferId.NO_BUFFER);

        Ethernet eth = new Ethernet()
                .setSourceMACAddress(instance.getServerMac())
                .setDestinationMACAddress(chaddr)
                .setEtherType(EthType.IPv4);

        IPv4 ip = new IPv4()
                .setSourceAddress(instance.getServerIP())
                .setProtocol(IpProtocol.UDP)
                .setTtl((byte) 64);
        if (dstIPAddr.equals(IPv4Address.NONE)) {
            ip.setDestinationAddress(IPv4Address.NO_MASK); // Broadcast IP
        } else {
            ip.setDestinationAddress(dstIPAddr);
        }

        UDP udp = new UDP()
                .setDestinationPort(UDP.DHCP_CLIENT_PORT)
                .setSourcePort(UDP.DHCP_SERVER_PORT);

        DHCP dhcpAck = setDHCPAck(instance, chaddr, yiaddr, giaddr, xid, requestOrder);
        eth.setPayload(ip.setPayload(udp.setPayload(dhcpAck)));
        pob.setInPort(OFPort.ANY);

        List<OFAction> actions = new ArrayList<>();
        actions.add(sw.getOFFactory().actions().output(inPort, 0xffFFffFF));
        pob.setActions(actions);
        pob.setData(eth.serialize());

        return pob.build();
    }

    private DHCP setDHCPAck(DHCPInstance instance, MacAddress chaddr, IPv4Address yiaddr, IPv4Address giaddr,
                            int xid, List<Byte> requestOrder) {
        DHCP dhcpAck = new DHCP()
                        .setOpCode(DHCP.DHCPOpCode.OpCode_Reply.getCode())
                        .setHardwareType((byte) 1)
                        .setHardwareAddressLength((byte) 6)
                        .setHops((byte) 0)
                        .setTransactionId(xid)
                        .setSeconds((short) 0)
                        .setFlags((short) 0)
                        .setClientIPAddress(IPv4Address.FULL_MASK) // unassigned IP
                        .setYourIPAddress(yiaddr)
                        .setServerIPAddress(instance.getServerIP())
                        .setGatewayIPAddress(giaddr)
                        .setClientHardwareAddress(chaddr);

        List<DHCPOption> ackOptions = new ArrayList<>();

        DHCPOption newOption = new DHCPOption()
                                .setCode(DHCPOptionCode.OptionCode_MessageType.getCode())
                                .setData(new byte[]{DHCP.DHCPMessageType.ACK.getCode()})
                                .setLength((byte) 1);

        ackOptions.add(newOption);
        for (Byte specificRequest : requestOrder) {
            newOption = new DHCPOption();
            if (specificRequest == DHCPOptionCode.OptionCode_SubnetMask.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_SubnetMask.getCode())
                         .setData(instance.getSubnetMask().getBytes())
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Router.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Router.getCode())
                         .setData(instance.getRouterIP().getBytes())
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DomainName.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DomainName.getCode())
                         .setData(instance.getDomainName().getBytes())
                         .setLength((byte) instance.getDomainName().getBytes().length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DNS.getCode()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getDNSServers());		// Convert List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_DNS.getCode())
                         .setData(byteArray)
                         .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Broadcast_IP.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Broadcast_IP.getCode())
                         .setData(instance.getBroadcastIP().getBytes())
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DHCPServerID.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DHCPServerID.getCode())
                         .setData(instance.getServerIP().getBytes())
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_LeaseTime.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_LeaseTime.getCode())
                         .setData(DHCPServerUtils.intToBytes(instance.getLeaseTimeSec()))
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_NTP_IP.getCode()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getNtpServers());		// Convert List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_NTP_IP.getCode())
                         .setData(byteArray)
                         .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OPtionCode_RebindingTime.getCode()) {
                newOption.setCode(DHCPOptionCode.OPtionCode_RebindingTime.getCode())
                         .setData(DHCPServerUtils.intToBytes(instance.getRebindTimeSec()))
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_RenewalTime.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_RenewalTime.getCode())
                         .setData(DHCPServerUtils.intToBytes(instance.getRenewalTimeSec()))
                         .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_IPForwarding.getCode()) {
                newOption.setCode(DHCPOptionCode.OptionCode_IPForwarding.getCode())
                         .setData(DHCPServerUtils.intToBytes( instance.getIpforwarding() ? 1 : 0 ))
                         .setLength((byte) 1);

            }else {
                log.debug("Setting specific request for ACK failed");
            }
            ackOptions.add(newOption);

        }

        newOption = new DHCPOption()
                    .setCode(DHCPOptionCode.OptionCode_END.getCode())
                    .setLength((byte) 0);
        ackOptions.add(newOption);

        dhcpAck.setOptions(ackOptions);
        return dhcpAck;

    }

    public OFPacketOut createDHCPNak(DHCPInstance instance, IOFSwitch sw, OFPort inPort, MacAddress chaddr,
                                     IPv4Address giaddr, int xid) {

        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        pob.setBufferId(OFBufferId.NO_BUFFER);

        Ethernet eth = new Ethernet()
                        .setSourceMACAddress(instance.getServerMac())
                        .setDestinationMACAddress(chaddr)
                        .setEtherType(EthType.IPv4);

        IPv4 ip = new IPv4()
                    .setDestinationAddress(IPv4Address.NO_MASK)  // broadcast IP
                    .setSourceAddress(instance.getServerIP())
                    .setProtocol(IpProtocol.UDP)
                    .setTtl((byte) 64);

        UDP udp = new UDP()
                    .setDestinationPort(UDP.DHCP_CLIENT_PORT)
                    .setSourcePort(UDP.DHCP_SERVER_PORT);

        DHCP dhcpNAK = setDHCPNak(instance, chaddr, giaddr, xid);
        eth.setPayload(ip.setPayload(udp.setPayload(dhcpNAK)));
        pob.setInPort(OFPort.ANY);

        List<OFAction> actions = new ArrayList<>();
        actions.add(sw.getOFFactory().actions().output(inPort, 0xffFFffFF));
        pob.setActions(actions);
        pob.setData(eth.serialize());

        return pob.build();

    }

    private DHCP setDHCPNak(DHCPInstance instance, MacAddress chaddr, IPv4Address giaddr, int xid) {
        DHCP dhcpNak = new DHCP()
                        .setOpCode(DHCP.DHCPOpCode.OpCode_Reply.getCode())
                        .setHardwareType((byte) 1)
                        .setHardwareAddressLength((byte) 6)
                        .setHops((byte) 0)
                        .setTransactionId(xid)
                        .setSeconds((short) 0)
                        .setFlags((short) 0)
                        .setClientIPAddress(IPv4Address.FULL_MASK)  // unassigned IP
                        .setYourIPAddress(IPv4Address.FULL_MASK)
                        .setServerIPAddress(instance.getServerIP())
                        .setGatewayIPAddress(giaddr)
                        .setClientHardwareAddress(chaddr);

        List<DHCPOption> nakOptions = new ArrayList<>();
        DHCPOption newOption;
        newOption = new DHCPOption()
                        .setCode(DHCPOptionCode.OptionCode_MessageType.getCode())
                        .setData(new byte[]{DHCP.DHCPMessageType.NAK.getCode()})
                        .setLength((byte) 1);
        nakOptions.add(newOption);

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_DHCPServerID.getCode())
                .setData(instance.getServerIP().getBytes())
                .setLength((byte) 4);
        nakOptions.add(newOption);

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_END.getCode())
                .setLength((byte) 0);
        nakOptions.add(newOption);

        dhcpNak.setOptions(nakOptions);
        return dhcpNak;

    }

}