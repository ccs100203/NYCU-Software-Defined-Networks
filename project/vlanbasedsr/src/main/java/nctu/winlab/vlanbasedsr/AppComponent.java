/*
 * Copyright 2022-present Open Networking Foundation
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
package nctu.winlab.vlanbasedsr;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
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
import org.onlab.packet.VlanId;
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
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.Host;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.IpAddress;


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
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    /* device & is edge switch */
    HashMap<DeviceId, Boolean> device_edge = new HashMap<DeviceId, Boolean>();
    /* device & its vlanId */
    HashMap<DeviceId, VlanId> device_vlan = new HashMap<DeviceId, VlanId>();
    /* device & its subnet IP prefix */
    HashMap<DeviceId, IpPrefix> device_subnet = new HashMap<DeviceId, IpPrefix>();
    /* connectpoint and its Mac address */
    HashMap<ConnectPoint, MacAddress> cp_mac = new HashMap<ConnectPoint, MacAddress>();

    private final NameConfigListener cfgListener = new NameConfigListener();
    private final ConfigFactory factory = 
        new ConfigFactory<ApplicationId, NameConfig>(APP_SUBJECT_FACTORY, NameConfig.class, "VlanConfig") {
            @Override
            public NameConfig createConfig() {
                return new NameConfig();
            }
        };

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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.vlanbasedsr");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        flowRuleService.removeFlowRulesById(appId);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        log.info("Reconfigured");
    }

    /* installRule internal network, put into table[0] & table[1] */
    private void installRule_int(ConnectPoint dstCp) {
        log.info("install internal, CP: "+ dstCp.toString() + " Mac: " + cp_mac.get(dstCp).toString());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                    .setOutput(dstCp.port())
                                    .build();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

        /* install to table[0] */
        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selector.matchEthDst(cp_mac.get(dstCp))
                                    .build())
                .withTreatment(treatment)
                .withPriority(30)
                .fromApp(appId)
                .makeTemporary(300)
                .forDevice(dstCp.deviceId())
                .forTable(0)
                .build();
        flowRuleService.applyFlowRules(flowRule);

        /* install to table[1] */
        FlowRule flowRule2 = DefaultFlowRule.builder()
                .withSelector(selector.matchEthDst(cp_mac.get(dstCp))
                                    .build())
                .withTreatment(treatment)
                .withPriority(30)
                .fromApp(appId)
                .makeTemporary(300)
                .forDevice(dstCp.deviceId())
                .forTable(1)
                .build();
        flowRuleService.applyFlowRules(flowRule2);
    }

    /* installRule external network, push Vlan, then output */
    private void installRule_push(ConnectPoint srcCp, IpPrefix srcIp, IpPrefix dstIp, VlanId vlan) {
        log.info("push vlan, CP: " + srcCp.toString());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                    .pushVlan()
                                    .setVlanId(vlan)
                                    .setOutput(srcCp.port())
                                    .build();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
        .withSelector(selector.matchIPSrc(srcIp)
                                .matchIPDst(dstIp)
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .build())
        .withTreatment(treatment)
        .withPriority(40)
        .withFlag(ForwardingObjective.Flag.VERSATILE)
        .fromApp(appId)
        .makeTemporary(300)
        .add();
        flowObjectiveService.forward(srcCp.deviceId(), forwardingObjective);
    }

    /* installRule external network, pass packet, just output */
    private void installRule_pass(ConnectPoint srcCp, IpPrefix srcIp, IpPrefix dstIp, VlanId vlan) {
        log.info("pass packet, CP: " + srcCp.toString());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                    .setOutput(srcCp.port())
                                    .build();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
        .withSelector(selector.matchIPSrc(srcIp)
                                .matchIPDst(dstIp)
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchVlanId(vlan)
                                .build())
        .withTreatment(treatment)
        .withPriority(40)
        .withFlag(ForwardingObjective.Flag.VERSATILE)
        .fromApp(appId)
        .makeTemporary(300)
        .add();
        flowObjectiveService.forward(srcCp.deviceId(), forwardingObjective);
    }

    /* installRule external network, pop Vlan, then goto table[1] */
    private void installRule_pop(ConnectPoint dstCp, IpPrefix srcIp, IpPrefix dstIp, VlanId vlan) {
        log.info("pop vlan, CP: " + dstCp.toString());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                    .popVlan()
                                    .transition(1)
                                    .build();

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
        .withSelector(selector.matchIPSrc(srcIp)
                                .matchIPDst(dstIp)
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchVlanId(vlan)
                                .build())
        .withTreatment(treatment)
        .withPriority(40)
        .withFlag(ForwardingObjective.Flag.VERSATILE)
        .fromApp(appId)
        .makeTemporary(300)
        .add();
        flowObjectiveService.forward(dstCp.deviceId(), forwardingObjective);
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);

                if (config != null) {
                    /* get config */
                    ArrayList<DeviceId> deviceIds = new ArrayList<DeviceId>();
                    ArrayList<Boolean> isEdges = new ArrayList<Boolean>();
                    ArrayList<VlanId> vlans = new ArrayList<VlanId>();
                    ArrayList<IpPrefix> subnets = new ArrayList<IpPrefix>();
                    for (String it : config.deviceid())
                        deviceIds.add(DeviceId.deviceId(it));
                    for (Boolean it : config.isedge())
                        isEdges.add(it);
                    for (String it : config.vlanid())
                        vlans.add(VlanId.vlanId(it));
                    for (String it : config.subnet())
                        subnets.add(IpPrefix.valueOf(it));

                    /* record deviceId & edge switch */
                    for (int i=0; i < config.switchnum(); ++i) {
                        device_edge.put(deviceIds.get(i), isEdges.get(i));
                    }
                    /* record deviceId & vlan */
                    for (int i=0; i < config.switchnum(); ++i) {
                        device_vlan.put(deviceIds.get(i), vlans.get(i));
                    }

                    /* record deviceId & subnet */
                    for (int i=0; i < config.switchnum(); ++i) {
                        device_subnet.put(deviceIds.get(i), subnets.get(i));
                    }

                    /* record ConnectPoint & Mac */
                    for (Host it : hostService.getHosts()) {
                        cp_mac.put(ConnectPoint.deviceConnectPoint(it.location().toString()), it.mac());
                    }

                    /* test, show hosts info */
                    for (Host it : hostService.getHosts()) {
                        log.info("HOST: " + it.toString());
                    }

                    /* tackle flow rule */
                    for (DeviceId it1 : deviceIds) {
                        for (DeviceId it2 : deviceIds) {
                            /* install flow rule */
                            /* if there are the same switch, represent the same Vlan */
                            if (it1.equals(it2)) {
                                log.info("same subnet same switch");
                                /* install local rules for all hosts in this edge switch */
                                for (ConnectPoint it3 : edgePortService.getEdgePoints(it2)) {
                                    installRule_int(it3);
                                }                     
                            } /* there are different switches, that is different Vlan */
                            else {
                                /* both are edge switches */
                                if (device_edge.get(it1) && device_edge.get(it2)) {
                                    log.info("both are edge switches");
                                    Set<Path> myPath = pathService.getPaths(it1, it2);
                                    for (Path p : myPath) {
                                        // log.info("A PATH-----");
                                        for (Link l : p.links()) {
                                            // log.info("Link--- " + l.toString());
                                            /* first step, first switch, need push vlan */
                                            if (l.src().deviceId().equals(it1)) {
                                                installRule_push(l.src(), device_subnet.get(it1), device_subnet.get(it2), device_vlan.get(it2));
                                            } /* middle switch, just pass packet */
                                            else {
                                                installRule_pass(l.src(), device_subnet.get(it1), device_subnet.get(it2), device_vlan.get(it2));
                                            }

                                            /* if packet goto the destination vlan, pop vlan */
                                            if (l.dst().deviceId().equals(it2)) {
                                                installRule_pop(l.dst(), device_subnet.get(it1), device_subnet.get(it2), device_vlan.get(it2));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
