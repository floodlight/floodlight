/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core;

import java.util.List;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * An interface to describe classes that write OF messages.
 * E.g. IOFSwitch, IOFConnection
 */

public interface IOFMessageWriter{

    /**
     * Writes to the OFMessage to the output stream.
     *
     * <p><b>Note:</b> this method has fire-and-forget semantics. When the connection is
     * not currently connected, it will silently discard the messages.
     *
     * @param m
     */
    void write(OFMessage m);

    /**
     * Writes the list of messages to the output stream.
     *
     * <p><b>Note:</b> this method has fire-and-forget semantics. When the connection is
     * not currently connected, it will silently discard the messages.
     *
     * @param msglist
     */
    void write(Iterable<OFMessage> msglist);
    
    /** write an OpenFlow Request message, register for a single corresponding reply message
     *  or error message.
     *
     * @param request
     * @return a Future object that can be used to retrieve the asynchrounous
     *         response when available.
     *
     *         If the connection is not currently connected, will
     *         return a Future that immediately fails with a @link{SwitchDisconnectedException}.
     */
    <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request);

    /** write a Stats (Multipart-) request, register for all corresponding reply messages.
     * Returns a Future object that can be used to retrieve the List of asynchronous
     * OFStatsReply messages when it is available.
     *
     * @param request stats request
     * @return Future object wrapping OFStatsReply
     *         If the connection is not currently connected, will
     *         return a Future that immediately fails with a @link{SwitchDisconnectedException}.
     */
    <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(
            OFStatsRequest<REPLY> request);
}
