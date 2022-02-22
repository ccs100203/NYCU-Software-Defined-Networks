/*
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.ProxyArp;

import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.core.CoreService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.InboundPacket;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpAddress.Version;
import org.onlab.packet.ARP;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.ConnectPoint;
import org.onlab.packet.VlanId;



import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    private MyPacketProcessor processor = new MyPacketProcessor();
    // It's my iptable in controller
    private Map<IpAddress, DeviceId> ip_dev_table = new HashMap<>();
    private Map<IpAddress, MacAddress> ip_mac_table = new HashMap<>();
    private Map<IpAddress, PortNumber> ip_port_table = new HashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.ProxyArp");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        packetService.removeProcessor(processor);
        flowRuleService.removeFlowRulesById(appId);
        processor = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        requestIntercepts();
        log.info("Reconfigured");
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        // request the ARP packet to do pactek_in to the controller 
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /* Send a packetout to the specified device and port with an Ethernet packet */
    private void uniPacketout(DeviceId deviceId, PortNumber port, Ethernet eth) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setOutput(port)
                        .build();
        
        
        OutboundPacket outpkt = new DefaultOutboundPacket(deviceId, treatment,
                                    ByteBuffer.wrap(eth.serialize()));

        packetService.emit(outpkt);
        /**
        I couldn't use follow method, because of the convertion error.
        
        Method:
            edgePortService.emitPacket(deviceId, ByteBuffer.wrap(eth.serialize()), treatment);
        
        Error Message:
            incompatible types: org.onosproject.net.flow.TrafficTreatment cannot be converted 
            to java.util.Optional<org.onosproject.net.flow.TrafficTreatment>
        */
    }

    /**
     * Packet processor
     */
    private class MyPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            if (ethPkt.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }
            
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber port =  pkt.receivedFrom().port();

            ARP arpPacket = (ARP) ethPkt.getPayload();
            IpAddress srcIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"), arpPacket.getSenderProtocolAddress());
            IpAddress dstIp = IpAddress.valueOf(IpAddress.Version.valueOf("INET"), arpPacket.getTargetProtocolAddress());
            // log.info("deviceId  "+deviceId.toString());
            log.info("-----Proxy-----");
            log.info("srcIp  " + srcIp.toString());
            log.info("dstIp  " + dstIp.toString());
            log.info("srcMac  " + srcMac.toString());
            log.info("dstMac  " + dstMac.toString());

            // put/update source ip/mac/deviceID/port into table
            ip_mac_table.put(srcIp, srcMac);
            ip_dev_table.put(srcIp, deviceId);
            ip_port_table.put(srcIp, port);

            // if OpCode is 1, it's ARP request
            if(arpPacket.getOpCode() == 1) {
                /* the destibation ip is existed, reply by controller */
                if(ip_mac_table.containsKey(dstIp)) {
                    log.info("Proxy: TABLE HIT, Requested MAC = " + ip_mac_table.get(dstIp).toString());

                    Ethernet outpkt = ARP.buildArpReply(dstIp.getIp4Address(), ip_mac_table.get(dstIp), ethPkt);
                    uniPacketout(deviceId, port, outpkt);                    

                } /* the destibation ip is not existed, packet out to other switches */
                else {
                    log.info("Proxy: TABLE MISS, Send request to edge ports");
                    for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                        // do not send request to himself
                        if (cp.port().equals(port) && cp.deviceId().equals(deviceId)) {
                            log.info("Proxy: In send ARP request, checking \'continue\' work");
                            continue;
                        }
                        Ethernet outpkt = ARP.buildArpRequest(srcMac.toBytes(), srcIp.toOctets(),
                                            dstIp.toOctets(), VlanId.NO_VID);
                        uniPacketout(cp.deviceId(), cp.port(), outpkt);
                    }
                }

            } /* receiver replies arping, it's ARP reply */
            else if (arpPacket.getOpCode() == 2) {
                log.info("Proxy: RECV REPLY, Requested MAC = " + srcMac.toString());
                /* ethPkt is the real ARP Reply */
                uniPacketout(ip_dev_table.get(dstIp), ip_port_table.get(dstIp), ethPkt);
            } /* other situation */
            else {
                log.info("Proxy: arpPacket.getOpCode(): ", arpPacket.getOpCode());
            }

        }
    }
}
