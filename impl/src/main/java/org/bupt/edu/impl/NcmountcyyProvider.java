/*
 * Copyright © 2017 cyy and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.edu.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.bupt.edu.impl.listener.LoggingNotificationListener;
import org.bupt.edu.impl.listener.PerformanceAwareNotificationListener;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.RpcConsumerRegistry;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.GlobalInterfaceConfiguration;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107.InterfaceConfigurations;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.cfg.rev150107._interface.configurations.InterfaceConfiguration;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107.InterfaceProperties;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.properties.DataNodes;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.properties.data.nodes.DataNode;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.properties.data.nodes.data.node.Locationviews;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.properties.data.nodes.data.node.locationviews.Locationview;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.table.Interfaces;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ifmgr.oper.rev150107._interface.table.interfaces.Interface;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.RouterStatic;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.AddressFamilyBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.address.family.address.family.Vrfipv4Builder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.Vrfs;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.Vrf;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.router._static.vrfs.VrfKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.VrfPrefixes;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.VrfPrefixesBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefix;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefixBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.prefix.table.vrf.prefixes.VrfPrefixKey;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.VrfRouteBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.vrf.route.VrfNextHopsBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.route.vrf.route.vrf.next.hops.NextHopAddressBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.vrf.unicast.VrfUnicastBuilder;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.xr.types.rev150119.CiscoIosXrString;
import org.opendaylight.yang.gen.v1.org.opendaylight.coretutorials.ncmount.example.notifications.rev150611.ExampleNotificationsListener;
import org.opendaylight.yang.gen.v1.org.opendaylight.coretutorials.ncmount.example.notifications.rev150611.VrfRouteNotification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.NotificationsService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmountcyy.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmountcyy.rev150105.show.node.output.IfCfgDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmountcyy.rev150105.show.node.output._if.cfg.data.Ifc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmountcyy.rev150105.show.node.output._if.cfg.data.IfcBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmountcyy.rev150105.show.node.output._if.cfg.data.IfcKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ncmountcyy.rev150105.write.routes.input.Route;
import org.opendaylight.yang.gen.v1.urn.opendaylight.test.rev141017.Cont;
import org.opendaylight.yang.gen.v1.urn.opendaylight.test.rev141017.ContBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType.DELETE;

public class NcmountcyyProvider implements DataTreeChangeListener<Topology>,NcmountcyyService {

    private static final Logger LOG = LoggerFactory.getLogger(NcmountcyyProvider.class);

    public static final InstanceIdentifier<Topology> NETCONF_TOPO_IID =
            InstanceIdentifier
                    .create(NetworkTopology.class)
                    .child(Topology.class,
                            new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())));


    private final DataBroker dataBroker;
    private ListenerRegistration<DataTreeChangeListener> dtclReg;
    private final RpcProviderRegistry rpcProviderRegistry;
    private MountPointService mountService;
    private RpcResult<Void> SUCCESS = RpcResultBuilder.<Void>success().build();

    public NcmountcyyProvider(final DataBroker dataBroker,final RpcProviderRegistry rpcProviderRegistry,
                              final MountPointService mountService) {
        this.mountService=mountService;
        this.dataBroker = dataBroker;
        this.rpcProviderRegistry=rpcProviderRegistry;
        if(dataBroker!=null){
            this.dtclReg = dataBroker.registerDataTreeChangeListener(new DataTreeIdentifier(LogicalDatastoreType.CONFIGURATION,NETCONF_TOPO_IID),this);
        }

    }


    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("NcmountProvider Session Initiated");
        rpcProviderRegistry.addRpcImplementation(NcmountcyyService.class,
                new NcmountcyyProvider(dataBroker,rpcProviderRegistry,mountService));

    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("NcmountProvider Closed");
    }

    @Override
    public Future<RpcResult<ListNodesOutput>> listNodes() {
        List<Node> nodes;
        ListNodesOutputBuilder outBld = new ListNodesOutputBuilder();

        ReadTransaction tx=dataBroker.newReadOnlyTransaction();

        try{
            nodes=tx.read(LogicalDatastoreType.OPERATIONAL,NETCONF_TOPO_IID).checkedGet().get().getNode();
        }catch(ReadFailedException e){
            LOG.error("Failed to read node config from datastore",e);
            throw new IllegalStateException(e);
        }

        List<String> results = new ArrayList<String>();
        for(Node node:nodes){
            LOG.info("Node:{}",node);
            NetconfNode nnode = node.getAugmentation(NetconfNode.class);
            if (nnode != null){
                NetconfNodeConnectionStatus.ConnectionStatus csts = nnode.getConnectionStatus();
                if(csts== NetconfNodeConnectionStatus.ConnectionStatus.Connected){
                    List<String> capabilities =
                            nnode.getAvailableCapabilities().getAvailableCapability().stream().map(cp ->
                                    cp.getCapability()).collect(Collectors.toList());
                    LOG.info("Capabilities: {}",capabilities);
                }
            }
            results.add(node.getNodeId().getValue());
        }
        outBld.setNcOperNodes(results);
        return RpcResultBuilder.success(outBld.build()).buildFuture();

    }

    @Override
    public Future<RpcResult<Void>> writeTest(WriteTestInput input) {
        String content=input.getTestContent();

        Cont cont= new ContBuilder().setL(content).build();
        final InstanceIdentifier<Cont> contInstanceId= InstanceIdentifier.create(Cont.class);
        final Optional<MountPoint> mountPoint;
        try{
            mountPoint = mountService.getMountPoint(mountIds.get(input.getMountName()));
        }catch(ExecutionException e){
            throw new IllegalArgumentException(e);
        }
        final DataBroker dataBroker1 = mountPoint.get().getService(DataBroker.class).get();
        final WriteTransaction writeTransaction1 =dataBroker.newWriteOnlyTransaction();
        writeTransaction1.merge(LogicalDatastoreType.CONFIGURATION,contInstanceId,cont);
        final WriteTransaction writeTransaction = dataBroker1.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,contInstanceId,cont);
        final CheckedFuture<Void, TransactionCommitFailedException> submit = writeTransaction.submit();
        final CheckedFuture<Void, TransactionCommitFailedException> submit1 = writeTransaction1.submit();



        return Futures.transform(submit,new Function<Void,RpcResult<Void>>(){
            @Override
            public RpcResult<Void> apply(final Void result){
                LOG.info("write test succeed");
                return SUCCESS;
            }
        });

    }

    @Override
    public Future<RpcResult<ShowNodeOutput>> showNode(ShowNodeInput input) {
        LOG.info("showNode called, input{}",input);
        final Optional<MountPoint> xrNodeOptional = mountService.getMountPoint(NETCONF_TOPO_IID
                .child(Node.class, new NodeKey(new NodeId(input.getNodeName()))));

        Preconditions.checkArgument(xrNodeOptional.isPresent(),
                "Unable to locate mountpoint: %s, not mounted yet or not configured",
                input.getNodeName());
        final MountPoint xrNode=xrNodeOptional.get();

        final DataBroker xrNodeBroker = xrNode.getService(DataBroker.class).get();

        final ReadOnlyTransaction xrNodeReadTx = xrNodeBroker.newReadOnlyTransaction();

        InstanceIdentifier<InterfaceConfigurations> iid=InstanceIdentifier.create(InterfaceConfigurations.class);

        Optional<InterfaceConfigurations> ifConfig;
        try{
            ifConfig = xrNodeReadTx.read(LogicalDatastoreType.CONFIGURATION,iid).checkedGet();
        }catch (ReadFailedException e){
            throw new IllegalStateException("Unexpected error reading data from"+input.getNodeName(),e);
        }
        List<Ifc> ifcList=new ArrayList<Ifc>();
        if(ifConfig.isPresent()){
            List<InterfaceConfiguration> ifConfigs = ifConfig
                    .get()
                    .getInterfaceConfiguration();
            for(InterfaceConfiguration config : ifConfigs){
                LOG.info("Config for '{}':config {}",
                        config.getInterfaceName().getValue(),config);
                String ifcActive = config.getActive().getValue();
                String ifcName = config.getInterfaceName().getValue();
                ifcList.add(new IfcBuilder().setActive(ifcActive)
                        .setBandwidth(config.getBandwidth())
                        .setDescription(config.getDescription())
                        .setInterfaceName(ifcName)
                        .setLinkStatus(config.isLinkStatus()==Boolean.TRUE ?"Up":"Down")
                        .setKey(new IfcKey(ifcActive,ifcName)).build());
            }
        }else{
            LOG.info("No data present on path '{}' for mountpoint: {}",
                    iid,input.getNodeName());
        }

        InstanceIdentifier<DataNodes> idn = InstanceIdentifier.create(InterfaceProperties.class)
                .child(DataNodes.class);
        Optional<DataNodes> ldn;
        try{
            ldn=xrNodeReadTx.read(LogicalDatastoreType.OPERATIONAL,idn).checkedGet();
        }catch (ReadFailedException e){
            throw new IllegalStateException("Unexpected error reading data from"+input.getNodeName(),e);
        }
        if (ldn.isPresent()) {
            List<DataNode> dataNodes = ldn.get().getDataNode();
            for (DataNode node : dataNodes) {
                LOG.info("DataNode '{}'", node.getDataNodeName().getValue());

                Locationviews lw = node.getLocationviews();
                List<Locationview> locationViews = lw.getLocationview();
                for (Locationview view : locationViews) {
                    LOG.info("LocationView '{}': {}",
                            view.getKey().getLocationviewName().getValue(),
                            view);
                }

                Interfaces ifc = node.getSystemView().getInterfaces();
                List<Interface> ifList = ifc.getInterface();
                for (Interface intf : ifList) {
                    LOG.info("Interface '{}': {}",
                            intf.getInterface().getValue(), intf);
                }

            }
        } else {
            LOG.info("No data present on path '{}' for mountpoint: {}",
                    idn, input.getNodeName());
        }

        // Finally, we build the RPC response with the retrieved data and return
        ShowNodeOutput output = new ShowNodeOutputBuilder()
                .setIfCfgData(new IfCfgDataBuilder()
                        .setIfc(ifcList)
                        .build())
                .build();
        return RpcResultBuilder.success(output).buildFuture();

    }


    LoadingCache<String, KeyedInstanceIdentifier<Node, NodeKey>> mountIds = CacheBuilder.newBuilder()
            .maximumSize(20)
            .build(
                    new CacheLoader<String, KeyedInstanceIdentifier<Node, NodeKey>>() {
                        public KeyedInstanceIdentifier<Node, NodeKey> load(final String key) {
                            return NETCONF_TOPO_IID.child(Node.class, new NodeKey(new NodeId(key)));
                        }
                    });

    @Override
    public Future<RpcResult<Void>> writeRoutes(WriteRoutesInput input) {
        final Optional<MountPoint> mountPoint;
        try{
            mountPoint = mountService.getMountPoint(mountIds.get(input.getMountName()));
        }catch(ExecutionException e){
            throw new IllegalArgumentException(e);
        }

        final DataBroker dataBroker = mountPoint.get().getService(DataBroker.class).get();
        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        //get databroker and transaction through mountpoint

        final CiscoIosXrString name=new CiscoIosXrString(input.getVrfId());
        final InstanceIdentifier<Vrf> routesInstanceId = InstanceIdentifier.create(RouterStatic.class)
                .child(Vrfs.class).child(Vrf.class,new VrfKey(name));

        final VrfPrefixes transformedRoutes = new VrfPrefixesBuilder()
                .setVrfPrefix(Lists.transform(input.getRoute(),(Function<Route,VrfPrefix>) input1->{

                        final IpAddress prefix = new IpAddress(input1.getIpv4Prefix());
                        final IpAddress nextHop = new IpAddress(input1.getIpv4NextHop());
                        final long prefixLength = input1.getIpv4PrefixLength();
                        return new VrfPrefixBuilder()
                                .setVrfRoute(new VrfRouteBuilder()
                                        .setVrfNextHops(new VrfNextHopsBuilder()
                                                .setNextHopAddress(Collections.singletonList(new NextHopAddressBuilder()
                                                        .setNextHopAddress(nextHop)
                                                        .build()))
                                                .build())
                                        .build()).setPrefix(prefix)
                                .setPrefixLength(prefixLength)
                                .setKey(new VrfPrefixKey(prefix,prefixLength))
                                .build();

                })).build();
        final Vrf newRoutes = new VrfBuilder()
                .setVrfName(name)
                .setKey(new VrfKey(name))
                .setAddressFamily(new AddressFamilyBuilder()
                        .setVrfipv4(new Vrfipv4Builder()
                                .setVrfUnicast(new VrfUnicastBuilder()
                                        .setVrfPrefixes(transformedRoutes)
                                        .build())
                                .build())
                        .build())
                .build();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION,routesInstanceId,newRoutes);

        final CheckedFuture<Void, TransactionCommitFailedException> submit = writeTransaction.submit();
        return Futures.transform(submit,new Function<Void,RpcResult<Void>>(){
            @Override
            public RpcResult<Void> apply(final Void result){
                LOG.info("{} Route(s) written to {}",input.getRoute().size(),input.getMountName());
                return SUCCESS;
            }
        });
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Topology>> changes) {
        // We need to handle the following types of events:
        // 1. Discovery of new nodes
        // 2. Status change in existing nodes
        // 3. Removal of existing nodes
        //LOG.info("OnDataChange, change: {}", changes);
        LOG.info("OnDataChange, change: {}", changes);

        // EXAMPLE: New node discovery
        // React to new Netconf nodes added to the Netconf topology or existing
        // Netconf nodes deleted from the Netconf topology
        for (DataTreeModification change: changes) {
            if (change.getRootNode().getIdentifier().getType()== NetconfNode.class) {
                NodeId nodeId = getNodeId(change.getRootPath().getRootIdentifier());
                LOG.info("NETCONF Node: {} was created", nodeId.getValue());

                // Not much can be done at this point, we need UPDATE event with
                // state set to connected
            }
        }

        // EXAMPLE: Status change in existing node(s)
        // React to data changes in Netconf nodes that are present in the
        // Netconf topology
        for (DataTreeModification change :changes) {
            if (change.getRootNode().getIdentifier().getType()== NetconfNode.class) {
                NodeId nodeId = getNodeId(change.getRootPath().getRootIdentifier());

                // We have a Netconf device
                NetconfNode nnode = (NetconfNode) change.getRootNode();
                NetconfNodeConnectionStatus.ConnectionStatus csts = nnode.getConnectionStatus();

                switch (csts) {
                    case Connected: {
                        // Fully connected, all services for remote device are
                        // available from the MountPointService.
                        LOG.info("NETCONF Node: {} is fully connected", nodeId.getValue());
                        List<String> capabilities =
                                nnode.getAvailableCapabilities().getAvailableCapability().stream().map(cp ->
                                        cp.getCapability()).collect(Collectors.toList());
                        LOG.info("Capabilities: {}", capabilities);

                        // Check if device supports our example notification and if it does, register a notification org.bupt.edu.impl.listener
                        if (capabilities.contains(QName.create(VrfRouteNotification.QNAME, "Example-notifications").toString())) {
                            registerNotificationListener(nodeId);
                        }

                        break;
                    }
                    case Connecting: {
                        // A Netconf device's will be in the 'Connecting' state
                        // initially, and go back to it after disconnect.
                        // Note that a device could be moving back and forth
                        // between the 'Connected' and 'Connecting' states for
                        // various reasons, such as disconnect from remote
                        // device, network connectivity loss etc.
                        LOG.info("NETCONF Node: {} was disconnected", nodeId.getValue());
                        break;
                    }
                    case UnableToConnect: {
                        // The maximum configured number of reconnect attempts
                        // have been reached. No more reconnects will be
                        // attempted by the Netconf Connector.
                        LOG.info("NETCONF Node: {} connection failed", nodeId.getValue());
                        break;
                    }
                }
            }
        }

        // EXAMPLE: Removal of an existing node from the Netconf topology
        for (DataTreeModification change: changes) {
            if (change.getRootNode().getModificationType()==DELETE)
            {
                final NodeId nodeId = getNodeId(change.getRootPath().getRootIdentifier());
                if (nodeId != null) {
                    // A User removed the Netconf connector for this node
                    // Before a node is removed, it changes its state to connecting
                    // (just as if it was disconnected). We may see this multiple
                    // times, since our org.bupt.edu.impl.listener is scoped to SUBTREE.
                    LOG.info("NETCONF Node: {} was removed", nodeId.getValue());
                }
            }
        }

    }
    private NodeId getNodeId(final InstanceIdentifier<?> path) {
        for (InstanceIdentifier.PathArgument pathArgument : path.getPathArguments()) {
            if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

                final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
                if (key instanceof NodeKey) {
                    return ((NodeKey) key).getNodeId();
                }
            }
        }
        return null;
    }
    private void registerNotificationListener(final NodeId nodeId) {
        final Optional<MountPoint> mountPoint;
        try {
            // Get mount point for specified device
            mountPoint = mountService.getMountPoint(mountIds.get(nodeId.getValue()));
        } catch (ExecutionException e) {
            throw new IllegalArgumentException(e);
        }

        // Instantiate notification org.bupt.edu.impl.listener
        final ExampleNotificationsListener listener;
        // The PerformanceAwareNotificationListener is a special version of org.bupt.edu.impl.listener that
        // measures the time until a specified number of notifications was received
        // The performance is calculated as number of received notifications / elapsed time in seconds
        // This is used for performance testing/measurements and can be ignored
        if (PerformanceAwareNotificationListener.shouldMeasurePerformance(nodeId) &&
                !nodeId.getValue().equals("controller-config")/*exclude loopback netconf connection*/) {
            listener = new PerformanceAwareNotificationListener(nodeId);
        } else {
            // Regular simple notification org.bupt.edu.impl.listener with a simple log message
            listener = new LoggingNotificationListener();
        }

        // Register notification org.bupt.edu.impl.listener
        final Optional<NotificationService> service1 = mountPoint.get().getService(NotificationService.class);
        LOG.info("Registering notification org.bupt.edu.impl.listener on {} for node: {}", VrfRouteNotification.QNAME, nodeId);
        final ListenerRegistration<ExampleNotificationsListener> accessTopologyListenerListenerRegistration =
                service1.get().registerNotificationListener(listener);

        // We have the org.bupt.edu.impl.listener registered, but we need to start the notification stream from device by
        // invoking the create-subscription rpc with for stream named "STREAM_NAME". "STREAM_NAME" is not a valid
        // stream name and serves only for demonstration
        // ---
        // This snippet also demonstrates how to invoke custom RPCs on a mounted netconf node
        // The rpc being invoked here can be found at: https://tools.ietf.org/html/rfc5277#section-2.1.1
        // Note that there is no yang model for it in ncmount, but it is in org.opendaylight.controller:ietf-netconf-notifications
        // which is a transitive dependency in ncmount-impl
        final String streamName = "STREAM_NAME";
        final Optional<RpcConsumerRegistry> service = mountPoint.get().getService(RpcConsumerRegistry.class);
        final NotificationsService rpcService = service.get().getRpcService(NotificationsService.class);
        final CreateSubscriptionInputBuilder createSubscriptionInputBuilder = new CreateSubscriptionInputBuilder();
        createSubscriptionInputBuilder.setStream(new StreamNameType(streamName));
        LOG.info("Triggering notification stream {} for node {}", streamName, nodeId);
        final Future<RpcResult<Void>> subscription = rpcService.createSubscription(createSubscriptionInputBuilder.build());
    }
}