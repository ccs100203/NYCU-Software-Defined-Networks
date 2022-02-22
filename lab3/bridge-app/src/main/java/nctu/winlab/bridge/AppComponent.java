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
package nctu.winlab.bridge;

import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
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

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    /** Some configurable property. */
    private String someProperty;
    private ApplicationId appId;
    private BridgePacketProcessor processor = new BridgePacketProcessor();
    // It's my flowtable in controller
    private Map<DeviceId, Map<MacAddress, PortNumber>> flowtable = new HashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;
    
    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        // Registers a new application by its name, which is expected to follow the reverse DNS convention, e.g. org.flying.circus.app
        appId = coreService.registerApplication("nctu.winlab.bridge");
        // Service for intercepting data plane packets and for emitting synthetic outbound packets.
        // Adds the specified processor to the list of packet processors. It will be added into the list in the order of priority. 
        // The higher numbers will be processing the packets after the lower numbers.
        packetService.addProcessor(processor, PacketProcessor.director(2));

        requestIntercepts();
        
        log.info("APP Start", appId.id());
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        packetService.removeProcessor(processor);
        flowRuleService.removeFlowRulesById(appId);

        processor = null;
        log.info("APP Stop");
    }

    @Modified
    public void modified(ComponentContext context) {
        // Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        // if (context != null) {
        //     someProperty = get(properties, "someProperty");
        // }
        requestIntercepts();

        log.info("APP Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
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


    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class BridgePacketProcessor implements PacketProcessor {

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

            DeviceId deviceId = pkt.receivedFrom().deviceId();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            PortNumber port =  pkt.receivedFrom().port();
            // If the deviceId is first entering, then creating a new table
            if(!flowtable.containsKey(deviceId)){
                Map<MacAddress, PortNumber> table_unit = new HashMap<>();
                flowtable.put(deviceId, table_unit);
                log.info("create a new table for switch");
            }
            // update source mac in table
            if(!flowtable.get(deviceId).containsKey(srcMac)){
                flowtable.get(deviceId).put(srcMac, port);
                log.info("New MAC " + srcMac.toString() + " added into the table on switch " + deviceId.toString());
            }

            // lookup destination mac in table
            if(flowtable.get(deviceId).containsKey(dstMac)){
                // packet_out to designated port & install rule
                log.info("Destination MAC " + srcMac.toString()  + " is found on " + deviceId.toString() + ", flow rule installed on the switch");
                // // install rule --- original
                // TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                //                             .setOutput(flowtable.get(deviceId).get(dstMac))
                //                             .build();

                // TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                                        
                // ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                //         .withSelector(selector.matchEthSrc(srcMac).matchEthDst(dstMac).build())
                //         .withTreatment(treatment)
                //         .withPriority(30)
                //         .withFlag(ForwardingObjective.Flag.VERSATILE)
                //         .fromApp(appId)
                //         .makeTemporary(30)
                //         .add();
                // flowObjectiveService.forward(deviceId, forwardingObjective);

                // install rule --- DEMO
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                            .setOutput(flowtable.get(deviceId).get(dstMac))
                                            .build();

                TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

                FlowRule flowRule = DefaultFlowRule.builder()
                        .withSelector(selector.matchEthSrc(srcMac).matchEthDst(dstMac).build())
                        .withTreatment(treatment)
                        .withPriority(30)
                        .fromApp(appId)
                        .makeTemporary(30)
                        .forDevice(deviceId)
                        .forTable(0)
                        .build();
                flowRuleService.applyFlowRules(flowRule);



                // packet_out --- original
                context.treatmentBuilder().setOutput(flowtable.get(deviceId).get(dstMac));
                context.send();
            }
            else{
                // packet_out with flooding
                log.info("Destination MAC " + srcMac.toString()  + " is not found on " + deviceId.toString() + ", packet flooded");
                context.treatmentBuilder().setOutput(port.FLOOD);
                context.send();
            }
        }

    }

}
