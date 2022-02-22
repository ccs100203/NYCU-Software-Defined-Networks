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
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.Path;
import org.onosproject.net.Link;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;

import org.onlab.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {AppComponent.class})
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    private ConnectPoint dhcpCp; // dhcp server connectPoint
    private MacAddress dhcpMac = MacAddress.valueOf("FF:FF:FF:FF:FF:FF"); // dhcp server MacAddress
    // It's my flowtable in controller
    private Map<DeviceId, Map<MacAddress, PortNumber>> flowtable = new HashMap<>();

    private DHCPPacketProcessor processor = new DHCPPacketProcessor();
    private final NameConfigListener cfgListener = new NameConfigListener();
    private final ConfigFactory factory = 
        new ConfigFactory<ApplicationId, NameConfig>(APP_SUBJECT_FACTORY, NameConfig.class, "UnicastDhcpConfig") {
            @Override
            public NameConfig createConfig() {
                return new NameConfig();
            }
        };
    /* demo */
    // private final ConfigFactory factory = 
    //     new ConfigFactory<ApplicationId, NameConfig>(APP_SUBJECT_FACTORY, NameConfig.class, "GetHostsInfo") {
    //         @Override
    //         public NameConfig createConfig() {
    //             return new NameConfig();
    //         }
    //     };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(2));

        requestIntercepts();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        withdrawIntercepts();
        packetService.removeProcessor(processor);
        processor = null;
        flowRuleService.removeFlowRulesById(appId);

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
        // request the IPv4 packet to do pactek_in to the controller 
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if (config != null) {
                    dhcpCp = ConnectPoint.deviceConnectPoint(config.name());
                    log.info("DHCP server is at {}", config.name());
                }
            }
        }
    }

    /* demo */
    // private class NameConfigListener implements NetworkConfigListener {
    //     @Override
    //     public void event(NetworkConfigEvent event) {
    //         if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
    //             && event.configClass().equals(NameConfig.class)) {
    //             NameConfig config = cfgService.getConfig(appId, NameConfig.class);
    //             if (config != null) {
    //                 // dhcpCp = ConnectPoint.deviceConnectPoint(config.name());
    //                 for(String it : config.name())
    //                     log.info("IP is at {}", it);
    //             }
    //         }
    //     }
    // }

    private void installRule(DeviceId dstId, MacAddress srcMac, MacAddress dstMac, PortNumber port) {
        log.info("installRule: {} -> {} through port {} on {}", srcMac.toString(), dstMac.toString(), port.toString(), dstId.toString());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                    .setOutput(port)
                                    .build();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
        .withSelector(selector.matchEthSrc(srcMac).matchEthDst(dstMac).build())
        .withTreatment(treatment)
        .withPriority(30)
        .withFlag(ForwardingObjective.Flag.VERSATILE)
        .fromApp(appId)
        .makeTemporary(30)
        .add();
        flowObjectiveService.forward(dstId, forwardingObjective);
    }

    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class DHCPPacketProcessor implements PacketProcessor {

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

            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            DeviceId deviceId = pkt.receivedFrom().deviceId();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber port =  pkt.receivedFrom().port();

            /* is DHCP DISCOVER / REQUEST */
            boolean isBroadcast = dstMac.toString().equals("FF:FF:FF:FF:FF:FF");
            
            /* if dhcp server is the source / sender */
            if (!isBroadcast) {
                log.info("From DHCP Server");
                dhcpMac = srcMac;
                log.info("current dhcp mac: "+ dhcpMac.toString());

                if(flowtable.get(deviceId).containsKey(dstMac)){
                    log.info("DHCP server -> Dst Mac: " + dstMac.toString());
                    installRule(deviceId, srcMac, dstMac, flowtable.get(deviceId).get(dstMac));
                    context.treatmentBuilder().setOutput(flowtable.get(deviceId).get(dstMac));
                    context.send();
                }else{
                    log.info("DHCP: Should not goto here");
                }
            } else {
                /* it's a host device */
                /* find a path from host to DHCP server */
                // log.info("HOST");
                log.info("current dhcp mac: "+ dhcpMac.toString());
                // If the deviceId is first entering, then creating a new table
                if(!flowtable.containsKey(deviceId)){
                    Map<MacAddress, PortNumber> table_unit = new HashMap<>();
                    flowtable.put(deviceId, table_unit);
                    log.info("DHCP: create a new table for "+deviceId.toString());
                }
                // update source mac in table
                flowtable.get(deviceId).put(srcMac, port);

                // if it's not a dhcp switch, send to other switch
                if(!deviceId.equals(dhcpCp.deviceId())) {
                    // Find path to dhcp server
                    Set<Path> myPath = pathService.getPaths(deviceId, dhcpCp.deviceId());

                    // intall rule
                    for (Path p : myPath) {
                        for (Link l : p.links()) {
                            installRule(l.src().deviceId(), srcMac, dhcpMac, l.src().port());
                            // packet_out
                            context.treatmentBuilder().setOutput(l.src().port());
                            context.send();
                        }
                    }
                } // On DHCP switch
                else {
                    log.info("DHCP SWITCH");
                    installRule(dhcpCp.deviceId(), srcMac, dhcpMac, dhcpCp.port());
                    context.treatmentBuilder().setOutput(dhcpCp.port());
                    context.send();
                }
            }
        }

    }
}
