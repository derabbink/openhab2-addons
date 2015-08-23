/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.converter.ZigBeeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeThingHandler extends BaseThingHandler {
    private List<ZigBeeThingChannel> thingChannelsCmd;
    private List<ZigBeeThingChannel> thingChannelsState;
    private List<ZigBeeThingChannel> thingChannelsPoll;

    private String nodeAddress;

    private Logger logger = LoggerFactory.getLogger(ZigBeeThingHandler.class);

    private ZigBeeCoordinatorHandler coordinatorHandler;

    private ScheduledFuture<?> pollingJob;

    public ZigBeeThingHandler(Thing zigbeeDevice) {
        super(zigbeeDevice);
    }

    @Override
    public void initialize() {
        // If the bridgeHandler hasn't initialised yet, then return
        if (coordinatorHandler == null) {
            return;
        }

        final String configAddress = (String) getConfig().get(ZigBeeBindingConstants.PARAMETER_MACADDRESS);
        logger.debug("Initializing ZigBee thing handler {}.", configAddress);
        nodeAddress = configAddress;

        //
        coordinatorHandler.deserializeNode(nodeAddress);

        // Until we get an update put the Thing into initialisation state
        updateStatus(ThingStatus.INITIALIZING);

        // Create the channels list to simplify processing incoming events
        thingChannelsCmd = new ArrayList<ZigBeeThingChannel>();
        thingChannelsPoll = new ArrayList<ZigBeeThingChannel>();
        thingChannelsState = new ArrayList<ZigBeeThingChannel>();
        for (Channel channel : getThing().getChannels()) {
            // Process the channel properties
            Map<String, String> properties = channel.getProperties();

            for (String key : properties.keySet()) {
                String[] bindingType = key.split(":");
                if (bindingType.length != 3) {
                    continue;
                }
                if (!ZigBeeBindingConstants.CHANNEL_CFG_BINDING.equals(bindingType[0])) {
                    continue;
                }

                String[] bindingProperties = properties.get(key).split(";");

                // TODO: Check length???

                // Get the clusters - comma separated
                String[] clusters = bindingProperties[0].split(",");

                // Convert the arguments to a map
                // - comma separated list of arguments "arg1=val1, arg2=val2"
                Map<String, String> argumentMap = new HashMap<String, String>();
                if (bindingProperties.length == 2) {
                    String[] arguments = bindingProperties[1].split(",");
                    for (String arg : arguments) {
                        String[] prop = arg.split("=");
                        argumentMap.put(prop[0], prop[1]);
                    }
                }

                // Add all the clusters...
                boolean first = true;
                for (String cc : clusters) {
                    String[] ccSplit = cc.split(":");

                    // The cluster must be "endpoint:converter"
                    // Endpoint is decimal, cluster is hex
                    if (ccSplit.length != 2) {
                    }

                    int endpointId = Integer.parseInt(ccSplit[0]);

                    // Get the data type
                    DataType dataType = DataType.DecimalType;
                    try {
                        dataType = DataType.valueOf(bindingType[2]);
                    } catch (IllegalArgumentException e) {
                        logger.warn("{}: Invalid item type defined ({}). Assuming DecimalType", nodeAddress, dataType);
                    }

                    String address = nodeAddress + "/" + endpointId;
                    ZigBeeThingChannel chan = new ZigBeeThingChannel(channel.getUID(), dataType, address, ccSplit[1],
                            argumentMap);

                    // First time round, and this is a command - then add the command
                    if (first && ("*".equals(bindingType[1]) || "Command".equals(bindingType[1]))) {
                        thingChannelsCmd.add(chan);
                    }

                    // Add the state and polling handlers
                    if ("*".equals(bindingType[1]) || "State".equals(bindingType[1])) {
                        thingChannelsState.add(chan);

                        if (first == true) {
                            thingChannelsState.add(chan);
                        }
                    }

                    // Initialise the converter
                    if (chan.converter != null) {
                        chan.converter.createConverter(this, chan, coordinatorHandler);

                        // And initialise it...
                        chan.converter.initializeConverter();
                    }

                    first = false;
                }
            }
        }

        logger.debug("{}: Initializing ZigBee thing handler", nodeAddress);

        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("{}: Polling...", nodeAddress);

                for (ZigBeeThingChannel channel : thingChannelsPoll) {
                    logger.debug("{}: Polling {}", nodeAddress, channel.getUID());
                    if (channel.converter == null) {
                        logger.debug("{}: Polling aborted as no converter found for {}", nodeAddress, channel.getUID());
                    } else {
                        channel.converter.handleRefresh();
                    }
                }
            }
        };

        pollingJob = scheduler.scheduleAtFixedRate(pollingRunnable, 60, 60, TimeUnit.SECONDS);
    }

    // @Override
    @Override
    protected void bridgeHandlerInitialized(ThingHandler thingHandler, Bridge bridge) {
        coordinatorHandler = (ZigBeeCoordinatorHandler) thingHandler;
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposes. Unregistering listener.");
        if (nodeAddress != null) {
            if (coordinatorHandler != null) {
                // coordinatorHandler.unsubscribeEvents(nodeAddress, this);
            }
            nodeAddress = null;
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
        }

        // Persist changes
        updateConfiguration(configuration);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (thingChannelsCmd == null) {
            logger.warn("No commands for channel {}.", channelUID);
            return;
        }
        if (coordinatorHandler == null) {
            logger.warn("Coordinator handler not found. Cannot handle command without coordinator.");
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        DataType dataType;
        try {
            dataType = DataType.valueOf(command.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            logger.warn("{}: Command received with no implementation ({}).", nodeAddress,
                    command.getClass().getSimpleName());
            return;
        }

        // Find the channel
        ZigBeeThingChannel cmdChannel = null;
        for (ZigBeeThingChannel channel : thingChannelsCmd) {
            if (channel.getUID().equals(channelUID) && channel.getDataType() == dataType) {
                cmdChannel = channel;
                break;
            }
        }

        if (cmdChannel == null) {
            logger.warn("{}: Command for unknown channel {}", nodeAddress, channelUID);
            return;
        }

        if (cmdChannel.converter == null) {
            logger.warn("{}: No converter set for {}", nodeAddress, channelUID);
            return;
        }

        cmdChannel.converter.handleCommand(command);
    }

    public void setChannelState(ChannelUID channel, State state) {
        updateState(channel, state);
        this.updateStatus(ThingStatus.ONLINE);
    }

    public class ZigBeeThingChannel {
        ChannelUID uid;
        String address;
        String cluster;
        ZigBeeConverter converter;
        DataType dataType;
        Map<String, String> arguments;

        ZigBeeThingChannel(ChannelUID uid, DataType dataType, String address, String cluster,
                Map<String, String> arguments) {
            this.uid = uid;
            this.arguments = arguments;
            this.cluster = cluster;
            this.address = address;
            this.dataType = dataType;

            // Get the converter
            this.converter = ZigBeeConverter.getConverter(cluster);
            if (this.converter == null) {
                logger.warn("No converter for {}, cluster {}", uid, cluster);
            }
        }

        public ChannelUID getUID() {
            return uid;
        }

        public String getCluster() {
            return cluster;
        }

        public String getAddress() {
            return address;
        }

        public DataType getDataType() {
            return dataType;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }
    }

    public enum DataType {
        DecimalType,
        HSBType,
        IncreaseDecreaseType,
        OnOffType,
        OpenClosedType,
        PercentType,
        StopMoveType;
    }

}
