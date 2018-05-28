package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.packet.DHCP.DHCPOptionCode;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 2/4/18
 * <p>
 * This class handles DHCP message based on DHCP spec.
 */
public class DHCPMessageHandler {
    protected static Logger log = LoggerFactory.getLogger(DHCPMessageHandler.class);

    /**
     * This function handles the DHCP Discover message and will send out DHCP Offer message to client
     *
     * @param sw
     * @param inPort
     * @param instance
     * @param clientIP
     * @param payload
     * @return
     */
    public OFPacketOut handleDHCPDiscover(@Nonnull IOFSwitch sw, @Nonnull OFPort inPort, @Nonnull DHCPInstance instance,
                                          @Nonnull IPv4Address clientIP, @Nonnull DHCP payload, @Nonnull Boolean dynamicLease) {
        /**  DHCP Discover Message
         * -- UDP src port = 68
         * -- UDP dst port = 67
         * -- IP src addr = 0.0.0.0
         * -- IP dst addr = 255.255.255.255
         * -- Opcode = 0x01
         * -- XID = transactionX
         * --
         * --	ciaddr = 0.0.0.0
         * --	yiaddr = 0.0.0.0
         * --	siaddr = 0.0.0.0
         * --	giaddr = 0.0.0.0
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
        IPv4Address giaddr = payload.getGatewayIPAddress();    // Will have GW IP if a relay agent was used
        IPv4Address yiaddr = payload.getYourIPAddress();
        MacAddress chaddr = payload.getClientHardwareAddress();
        List<Byte> requestOrder = new ArrayList<>();
        IPv4Address requestIP = null;

        for (DHCPOption option : payload.getOptions()) {
            if (option.getCode() == DHCPOptionCode.OptionCode_RequestedIP.getValue()) {
                requestIP = IPv4Address.of(option.getData());
                log.debug("Handling Discover Message: got requested IP");
            } else if (option.getCode() == DHCPOptionCode.OptionCode_RequestedParameters.getValue()) {
                requestOrder = getRequestedParameters(payload, false);
                log.debug("Handling Discover Message: got requested param list");
            }
        }

        DHCPPool dhcpPool = instance.getDHCPPool();
        long leaseTime = instance.getLeaseTimeSec();
        IPv4Address ipLease = null;

        if (!dynamicLease) {
            if (requestIP == null) {
                ipLease = dhcpPool.assignLeaseToClient(chaddr, leaseTime).get();
            } else {
                ipLease = dhcpPool.assignLeaseToClientWithRequestIP(requestIP, chaddr, leaseTime, false).get();
            }
        }else {
            if (requestIP == null) {
                ipLease = dhcpPool.assignDynamicLeaseToClient(chaddr, leaseTime).get();
            } else {
                ipLease = dhcpPool.assignLeaseToClientWithRequestIP(requestIP, chaddr, leaseTime, true).get();
            }

        }

        yiaddr = ipLease;
        DHCP dhcpOffer = buildDHCPOfferMessage(instance, chaddr, yiaddr, giaddr, xid, requestOrder);
        OFPacketOut dhcpPacketOut = buildDHCPOfferPacketOut(instance, sw, inPort, clientIP, dhcpOffer);
        return dhcpPacketOut;

    }

    public List<Byte> getRequestedParameters(@Nonnull DHCP dhcpPayload, boolean isInform) {
        ArrayList<Byte> requestOrder = new ArrayList<>();
        byte[] requests = dhcpPayload.getOption(DHCPOptionCode.OptionCode_RequestedParameters).getData();
        boolean requestedLeaseTime = false;
        boolean requestedRebindTime = false;
        boolean requestedRenewTime = false;

        for (byte specificRequest : requests) {
            if (specificRequest == DHCPOptionCode.OptionCode_SubnetMask.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_SubnetMask.getValue());

            } else if (specificRequest == DHCPOptionCode.OptionCode_Router.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_Router.getValue());

            } else if (specificRequest == DHCPOptionCode.OptionCode_DomainName.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_DomainName.getValue());

            } else if (specificRequest == DHCPOptionCode.OptionCode_DNS.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_DNS.getValue());

            } else if (specificRequest == DHCPOptionCode.OptionCode_LeaseTime.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_LeaseTime.getValue());
                requestedLeaseTime = true;

            } else if (specificRequest == DHCPOptionCode.OptionCode_DHCPServerID.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_DHCPServerID.getValue());

            } else if (specificRequest == DHCPOptionCode.OptionCode_Broadcast_IP.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_Broadcast_IP.getValue());

            } else if (specificRequest == DHCPOptionCode.OptionCode_NTP_IP.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_NTP_IP.getValue());

            } else if (specificRequest == DHCPOptionCode.OPtionCode_RebindingTime.getValue()) {
                requestOrder.add(DHCPOptionCode.OPtionCode_RebindingTime.getValue());
                requestedRebindTime = true;

            } else if (specificRequest == DHCPOptionCode.OptionCode_RenewalTime.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_RenewalTime.getValue());
                requestedRenewTime = true;

            } else if (specificRequest == DHCPOptionCode.OptionCode_IPForwarding.getValue()) {
                requestOrder.add(DHCPOptionCode.OptionCode_IPForwarding.getValue());
                log.debug("requested IP FORWARDING");

            } else {
                log.debug("Requested option 0x" + Byte.toString(specificRequest) + " not available");

            }
        }

        // We need to add these in regardless if the request list includes them
        if (!isInform) {
            if (!requestedLeaseTime) {
                requestOrder.add(DHCPOptionCode.OptionCode_LeaseTime.getValue());
                log.debug("added option LEASE TIME");
            }
            if (!requestedRenewTime) {
                requestOrder.add(DHCPOptionCode.OptionCode_RenewalTime.getValue());
                log.debug("added option RENEWAL TIME");
            }
            if (!requestedRebindTime) {
                requestOrder.add(DHCPOptionCode.OPtionCode_RebindingTime.getValue());
                log.debug("added option REBIND TIME");
            }
        }

        return requestOrder;
    }

    public DHCP buildDHCPOfferMessage(DHCPInstance instance, MacAddress chaddr, IPv4Address yiaddr, IPv4Address
            giaddr, int xid, List<Byte> requestOrder) {
        /**
         * DHCP Offer Message
         * -- UDP src port = 67
         * -- UDP dst port = 68
         * -- IP src addr = DHCP DHCPServer's IP
         * -- IP dst addr = 255.255.255.255
         * -- Opcode = 0x02
         *
         * -- XID = transactionX
         * -- ciaddr = 0.0.0.0
         * -- yiaddr = offer IP from DHCP pool
         * -- siaddr = 0.0.0.0
         * -- giaddr = 0.0.0.0
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
        DHCP dhcpOffer = new DHCP()
                .setOpCode(DHCP.DHCPOpCode.OpCode_Reply.getValue())
                .setHardwareType((byte) 1)
                .setHardwareAddressLength((byte) 6)
                .setHops((byte) 0)
                .setTransactionId(xid)
                .setSeconds((short) 0)
                .setFlags((short) 0)
                .setClientIPAddress(IPv4Address.FULL_MASK)
                .setServerIPAddress(IPv4Address.FULL_MASK)
                .setYourIPAddress(yiaddr)
                .setGatewayIPAddress(IPv4Address.FULL_MASK)
                .setClientHardwareAddress(chaddr);

        List<DHCPOption> dhcpOfferOptions = new ArrayList<>();

        DHCPOption newOption;
        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_MessageType.getValue())
                .setData(new byte[]{DHCP.DHCPMessageType.OFFER.getValue()})
                .setLength((byte) 1);
        dhcpOfferOptions.add(newOption);

        for (Byte specificRequest : requestOrder) {
            newOption = new DHCPOption();
            if (specificRequest == DHCPOptionCode.OptionCode_SubnetMask.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_SubnetMask.getValue())
                        .setData(instance.getSubnetMask().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Router.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Router.getValue())
                        .setData(instance.getRouterIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DomainName.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DomainName.getValue())
                        .setData(instance.getDomainName().getBytes())
                        .setLength((byte) instance.getDomainName().getBytes().length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DNS.getValue()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getDNSServers()); // Convert
                // List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_DNS.getValue())
                        .setData(byteArray)
                        .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Broadcast_IP.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Broadcast_IP.getValue())
                        .setData(instance.getBroadcastIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DHCPServerID.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DHCPServerID.getValue())
                        .setData(instance.getServerID().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_LeaseTime.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_LeaseTime.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getLeaseTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_NTP_IP.getValue()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getNtpServers()); // Convert
                // List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_NTP_IP.getValue())
                        .setData(byteArray)
                        .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OPtionCode_RebindingTime.getValue()) {
                newOption.setCode(DHCPOptionCode.OPtionCode_RebindingTime.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getRebindTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_RenewalTime.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_RenewalTime.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getRenewalTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_IPForwarding.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_IPForwarding.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getIpforwarding() ? 1 : 0))
                        .setLength((byte) 4);

            } else {
                log.debug("Setting specific request for OFFER failed");

            }

            dhcpOfferOptions.add(newOption);
        }

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_END.getValue())
                .setLength((byte) 0);
        dhcpOfferOptions.add(newOption);

        dhcpOffer.setOptions(dhcpOfferOptions);
        return dhcpOffer;

    }


    public OFPacketOut buildDHCPOfferPacketOut(@Nonnull DHCPInstance instance, @Nonnull IOFSwitch sw, @Nonnull OFPort inPort,
                                               @Nonnull IPv4Address clientIPAddress, @Nonnull DHCP dhcpOfferPacket) {

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
                                .setSourceAddress(instance.getServerID())
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
     * This function handles the DHCP Request message and response to the client(ACK or NAK) based on client state
     * machine,
     * while client states can be determined by "Request IP" and "Server IP".
     *
     * @param sw
     * @param inPort
     * @param instance
     * @param payload
     * @return
     */
    public OFPacketOut handleDHCPRequest(@Nonnull IOFSwitch sw, @Nonnull OFPort inPort, @Nonnull DHCPInstance instance,
                                         @Nonnull IPv4Address dstAddr, @Nonnull DHCP payload) {

        /** DHCP Request Message
         * -- UDP src port = 68
         * -- UDP dst port = 67
         * -- IP src addr = 0.0.0.0
         * -- IP dst addr = 255.255.255.255
         * -- Opcode = 0x01
         * -- XID = transactionX
         * --
         * -- ciaddr = 0.0.0.0
         * -- yiaddr = 0.0.0.0
         * -- siaddr = 0.0.0.0
         * -- giaddr = 0.0.0.0
         * -- chaddr = Client's MAC
         * --
         * -- Options:
         * --	Option 53 = DHCP Request
         * --	Option 50 = IP requested
         * --	Option 54 = DHCP Server Identifier
         **/
        int xid = payload.getTransactionId();
        IPv4Address yiaddr = payload.getYourIPAddress();
        IPv4Address giaddr = payload.getGatewayIPAddress();    // Will have value if a relay agent is used
        IPv4Address ciaddr = payload.getClientIPAddress();
        MacAddress chaddr = payload.getClientHardwareAddress();
        List<Byte> requestOrder = new ArrayList<>();
        IPv4Address requestIP = null;
        IPv4Address serverID = null;

        for (DHCPOption option : payload.getOptions()) {
            if (option.getCode() == DHCPOptionCode.OptionCode_DHCPServerID.getValue()) {
                serverID = IPv4Address.of(option.getData());
            } else if (option.getCode() == DHCPOptionCode.OptionCode_RequestedIP.getValue()) {
                requestIP = IPv4Address.of(option.getData());
            } else if (option.getCode() == DHCPOptionCode.OptionCode_RequestedParameters.getValue()) {
                requestOrder = getRequestedParameters(payload, false);
            }
        }

        boolean sendACK = false;
        boolean broadcastFlag = false;
        IDHCPService.ClientState clientState = determineClientState(requestIP, serverID, dstAddr);
        log.debug("Handling DHCP request from client {} who is in {} state.", chaddr, clientState);
        switch (clientState) {
            case INIT_REBOOT:
                sendACK = handleInitReboot(instance, requestIP, chaddr, ciaddr);
                // if giaddr is 0x0 then client is on same subnet as server, server will broadcast DHCP NAK
                if (giaddr.equals(IPv4Address.NONE) && !sendACK) {
                    dstAddr = IPv4Address.NO_MASK;
                    broadcastFlag = false;
                }
                // if giaddr is set then client is on a different subnet and dhcp relay agent is used
                // server will set broadcast bit in DHCP NAK
                else if (!giaddr.equals(IPv4Address.NONE) && !sendACK) {
                    broadcastFlag = true;
                }
                break;

            case SELECTING:
                sendACK = handleSelecting(instance, requestIP, serverID, chaddr);
                break;

            case RENEWING:
                sendACK = handleRenewing(instance, chaddr);
                break;

            case REBINDING:
                sendACK = handleRebinding(instance, chaddr);
                break;

            default:
                sendACK = false;
                break;
        }

        if (sendACK) {
            yiaddr = instance.getDHCPPool().getLeaseIP(chaddr).get();
            return createDHCPAck(instance, sw, inPort, chaddr, dstAddr, yiaddr, giaddr, xid, requestOrder);
        } else {
            return createDHCPNak(instance, sw, inPort, chaddr, giaddr, xid);
        }

    }

    /**
     * This function handles the DHCP Decline message
     * <p>
     * If client sends DHCP Decline message, it has discovered some other means that suggested network address is
     * already
     * in use. For example, when client received DHCP ACK, it will try to detect if there is other machine in network
     * using
     * same IP address. If client detects the conflict, it then will send a DHCP Decline message to server to decline
     * that IP lease, client will then re-send DHCP Discover message.
     *
     * @param instance
     * @param chaddr
     * @return
     */
    public boolean handleDHCPDecline(@Nonnull DHCPInstance instance, @Nonnull MacAddress chaddr) {
        return instance.getDHCPPool().cancelLeaseOfMac(chaddr);
    }

    /**
     * This function handles the DHCP Release message
     * <p>
     * When receive DHCP Release message, server should mark the address as not allocated.
     *
     * @param instance
     * @param chaddr
     * @return
     */
    public boolean handleDHCPRelease(@Nonnull DHCPInstance instance, @Nonnull MacAddress chaddr) {
        return instance.getDHCPPool().cancelLeaseOfMac(chaddr);
    }

    /**
     * This function handles the DHCP Inform message
     * <p>
     * Server responds the DHCP Inform message by sending a DHCP ACK directly to the address given in "ciaddr" field of
     * the DHCP Inform message. Server must not send a lease expiration time to the client and should not fill in
     * "yiaddr"
     *
     * @param sw
     * @param inPort
     * @param instance
     * @param dstAddr
     * @param payload
     * @return
     */
    public OFPacketOut handleDHCPInform(@Nonnull IOFSwitch sw, @Nonnull OFPort inPort, @Nonnull DHCPInstance instance,
                                        @Nonnull IPv4Address dstAddr, @Nonnull DHCP payload) {
        int xid = payload.getTransactionId();
        IPv4Address yiaddr = IPv4Address.NONE;
        IPv4Address giaddr = payload.getGatewayIPAddress();    // Will have GW IP if a relay agent was used
        MacAddress chaddr = payload.getClientHardwareAddress();
        List<Byte> requestOrder = new ArrayList<>();

        return createDHCPAck(instance, sw, inPort, chaddr, dstAddr, yiaddr, giaddr, xid, requestOrder);
    }

    public IDHCPService.ClientState determineClientState(@Nullable IPv4Address requstIP, @Nullable IPv4Address
                                                          serverID, IPv4Address dstAddr) {
        if (requstIP != null && serverID == null) {
            return IDHCPService.ClientState.INIT_REBOOT;
        } else if (requstIP != null && serverID != null) {
            return IDHCPService.ClientState.SELECTING;
        } else if (requstIP == null && serverID == null && !dstAddr.equals(IPv4Address.NO_MASK)) {  // unicast message
            return IDHCPService.ClientState.RENEWING;
        } else if (requstIP == null && serverID == null && dstAddr.equals(IPv4Address.NO_MASK)) {  // broadcast message
            return IDHCPService.ClientState.REBINDING;
        } else {
            return IDHCPService.ClientState.UNKNOWN; // This shouldn't happen
        }
    }

    /**
     * This function handles the "Init_Reboot" state of the client
     * <p>
     * In this state, client already has a valid lease starts up after a power-down or reboot, it attempts to verify its
     * lease and re-obtain its configuration parameters from DHCP server.
     *
     * Client will then send DHCP request w/ "request IP" but no "server ID" filled in.
     *
     * DHCP Server should send DHCPNAK message to client if "Request IP" is incorrect/invalid, or is on the wrong network
     *
     * @param requestIP
     * @return
     */
    public boolean handleInitReboot(DHCPInstance instance, IPv4Address requestIP,
                                    MacAddress chaddr, IPv4Address ciaddr) {
        boolean sendACK = true;

        if (requestIP == null) {
            sendACK = false;
        } else if (!ciaddr.equals(IPv4Address.NONE)) {
            sendACK = false;
        }else if (!instance.getDHCPPool().getLeaseIP(chaddr).isPresent()) {
            sendACK = false;
        } else if (!requestIP.equals(instance.getDHCPPool().getLeaseIP(chaddr).get())) {
            sendACK = false;
        } else {
            sendACK = true;
        }

        return sendACK;
    }

    /**
     * This function handles the "Selecting" state of the client
     * <p>
     * In this state, client already received the DHCP Offer message from one or more DHCP servers, it will choose one
     * and broadcast DHCP request w/ both "request IP" and "server ID" to tell all DHCP servers what its choice was
     *
     * If client preferred to receive DHCP offer from other server, we should cancel the DHCP binding that hold for
     * lease
     *
     * @param instance
     * @param requstIP
     * @param serverID
     * @param chaddr
     * @return
     */
    public boolean handleSelecting(DHCPInstance instance, IPv4Address requstIP, IPv4Address serverID, MacAddress chaddr) {
        boolean sendACK = true;
        // We're not the DHCP server that client wants
        if (!serverID.equals(instance.getServerID())) {
            sendACK = false;
        } else {
            Optional<IPv4Address> leaseIP = instance.getDHCPPool().getLeaseIP(chaddr);
            if (!leaseIP.isPresent()) {
                sendACK = false;
            }
            // Client wants a different IP than we have on file
            else if (!requstIP.equals(instance.getDHCPPool().getLeaseIP(chaddr).get())) {
                sendACK = false;
            } else {
                sendACK = true;
            }

        }

        if (!sendACK) {
            instance.getDHCPPool().cancelLeaseOfMac(chaddr);
        }

        return sendACK;
    }

    /**
     * This function handles the "Renewing" state of the client
     * <p>
     * In this state, client trying to renew its lease from original server.
     * Client send "Unicast" DHCP Request message(w/ no "request IP" and "server ID") to the original server, and waits
     * for the reply
     *
     * @param instance
     * @param chaddr
     * @return
     */
    public boolean handleRenewing(DHCPInstance instance, MacAddress chaddr) {
        boolean sendAck = false;
        Optional<DHCPBinding> lease = instance.getDHCPPool().getLeaseBinding(chaddr);
        if (!lease.isPresent()) {
            sendAck = false;
        } else {
            DHCPBinding l = lease.get();
            // If lease expired already, send NAK and cancel current lease; Client should start over to request an IP
            if (l.getCurrLeaseState() == LeasingState.EXPIRED) {
                sendAck = false;
                instance.getDHCPPool().cancelLeaseOfMac(chaddr);
            }else if (l.getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
                sendAck = false;
            }else { // If lease still alive, renew that lease
                sendAck = true;
                instance.getDHCPPool().renewLeaseOfMAC(chaddr, instance.getLeaseTimeSec());
            }
        }

        return sendAck;
    }

    /**
     * This function handles the "Rebinding" state of the client
     * <p>
     * Client failed to renew its lease with original server, it then will seek a lease extension with any server
     * Client will periodically send "Broadcast" DHCP Request message to the network, and binds to the server that
     * positively response.
     *
     * @param instance
     * @param chaddr
     * @return
     */
    public boolean handleRebinding(DHCPInstance instance, MacAddress chaddr) {
        boolean sendAck = false;
        Optional<DHCPBinding> binding = instance.getDHCPPool().getLeaseBinding(chaddr);
        if (!binding.isPresent()) {
            sendAck = false;
        }else if (binding.get().getCurrLeaseState() == LeasingState.PERMANENT_LEASED) {
            sendAck = false;
        } else {
            sendAck = true;
            instance.getDHCPPool().renewLeaseOfMAC(chaddr, instance.getLeaseTimeSec());
        }

        return sendAck;
    }


    public OFPacketOut createDHCPAck(DHCPInstance instance, IOFSwitch sw, OFPort inPort, MacAddress chaddr,
                                     IPv4Address dstIPAddr, IPv4Address yiaddr, IPv4Address giaddr,
                                     int xid, List<Byte> requestOrder) {
        /** DHCP ACK Message
         * -- UDP src port = 67
         * -- UDP dst port = 68
         * -- IP src addr = DHCP Server's IP
         * -- IP dst addr = 255.255.255.255 or client IP
         * -- Opcode = 0x02
         * -- XID = transactionX
         * --
         * -- ciaddr = 0.0.0.0
         * -- yiaddr = offered IP
         * -- siaddr = 0.0.0.0
         * -- giaddr = 0.0.0.0
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
                .setSourceAddress(instance.getServerID())
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

    public DHCP setDHCPAck(DHCPInstance instance, MacAddress chaddr, IPv4Address yiaddr, IPv4Address giaddr,
                            int xid, List<Byte> requestOrder) {
        DHCP dhcpAck = new DHCP()
                .setOpCode(DHCP.DHCPOpCode.OpCode_Reply.getValue())
                .setHardwareType((byte) 1)
                .setHardwareAddressLength((byte) 6)
                .setHops((byte) 0)
                .setTransactionId(xid)
                .setSeconds((short) 0)
                .setFlags((short) 0)
                .setClientIPAddress(IPv4Address.FULL_MASK) // unassigned IP
                .setServerIPAddress(IPv4Address.FULL_MASK)
                .setYourIPAddress(yiaddr)
                .setGatewayIPAddress(IPv4Address.FULL_MASK)
                .setClientHardwareAddress(chaddr);

        List<DHCPOption> ackOptions = new ArrayList<>();

        DHCPOption newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_MessageType.getValue())
                .setData(new byte[]{DHCP.DHCPMessageType.ACK.getValue()})
                .setLength((byte) 1);

        ackOptions.add(newOption);
        for (Byte specificRequest : requestOrder) {
            newOption = new DHCPOption();
            if (specificRequest == DHCPOptionCode.OptionCode_SubnetMask.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_SubnetMask.getValue())
                        .setData(instance.getSubnetMask().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Router.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Router.getValue())
                        .setData(instance.getRouterIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DomainName.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DomainName.getValue())
                        .setData(instance.getDomainName().getBytes())
                        .setLength((byte) instance.getDomainName().getBytes().length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DNS.getValue()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getDNSServers());        // Convert
                // List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_DNS.getValue())
                        .setData(byteArray)
                        .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OptionCode_Broadcast_IP.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_Broadcast_IP.getValue())
                        .setData(instance.getBroadcastIP().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_DHCPServerID.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_DHCPServerID.getValue())
                        .setData(instance.getServerID().getBytes())
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_LeaseTime.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_LeaseTime.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getLeaseTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_NTP_IP.getValue()) {
                byte[] byteArray = DHCPServerUtils.IPv4ListToByteArr(instance.getNtpServers());        // Convert
                // List<IPv4Address> to byte[]
                newOption.setCode(DHCPOptionCode.OptionCode_NTP_IP.getValue())
                        .setData(byteArray)
                        .setLength((byte) byteArray.length);

            } else if (specificRequest == DHCPOptionCode.OPtionCode_RebindingTime.getValue()) {
                newOption.setCode(DHCPOptionCode.OPtionCode_RebindingTime.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getRebindTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_RenewalTime.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_RenewalTime.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getRenewalTimeSec()))
                        .setLength((byte) 4);

            } else if (specificRequest == DHCPOptionCode.OptionCode_IPForwarding.getValue()) {
                newOption.setCode(DHCPOptionCode.OptionCode_IPForwarding.getValue())
                        .setData(DHCPServerUtils.intToBytes(instance.getIpforwarding() ? 1 : 0))
                        .setLength((byte) 1);

            } else {
                log.debug("Setting specific request for ACK failed");
            }
            ackOptions.add(newOption);

        }

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_END.getValue())
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
                .setSourceAddress(instance.getServerID())
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

    public DHCP setDHCPNak(DHCPInstance instance, MacAddress chaddr, IPv4Address giaddr, int xid) {
        DHCP dhcpNak = new DHCP()
                .setOpCode(DHCP.DHCPOpCode.OpCode_Reply.getValue())
                .setHardwareType((byte) 1)
                .setHardwareAddressLength((byte) 6)
                .setHops((byte) 0)
                .setTransactionId(xid)
                .setSeconds((short) 0)
                .setFlags((short) 0)
                .setClientIPAddress(IPv4Address.FULL_MASK)  // unassigned IP
                .setServerIPAddress(IPv4Address.FULL_MASK)
                .setYourIPAddress(IPv4Address.FULL_MASK)
                .setGatewayIPAddress(IPv4Address.FULL_MASK)
                .setClientHardwareAddress(chaddr);

        List<DHCPOption> nakOptions = new ArrayList<>();
        DHCPOption newOption;
        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_MessageType.getValue())
                .setData(new byte[]{DHCP.DHCPMessageType.NAK.getValue()})
                .setLength((byte) 1);
        nakOptions.add(newOption);

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_DHCPServerID.getValue())
                .setData(instance.getServerID().getBytes())
                .setLength((byte) 4);
        nakOptions.add(newOption);

        newOption = new DHCPOption()
                .setCode(DHCPOptionCode.OptionCode_END.getValue())
                .setLength((byte) 0);
        nakOptions.add(newOption);

        dhcpNak.setOptions(nakOptions);
        return dhcpNak;

    }


}