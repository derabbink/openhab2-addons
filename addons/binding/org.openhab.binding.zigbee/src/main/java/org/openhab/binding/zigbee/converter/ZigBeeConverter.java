/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.converter;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler.ZigBeeThingChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZigBeeClusterConverter class. Base class for all converters that convert between ZigBee clusters and openHAB
 * channels.
 *
 * @author Chris Jackson
 */
public abstract class ZigBeeConverter {
    private static Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    protected ZigBeeThingHandler thing = null;
    protected ZigBeeThingChannel channel = null;
    protected ZigBeeCoordinatorHandler coordinator = null;

    private static Map<String, Class<? extends ZigBeeConverter>> clusterMap = null;

    /**
     * Constructor. Creates a new instance of the {@link ZWaveCommandClassConverter} class.
     *
     */
    public ZigBeeConverter() {
        super();
    }

    public boolean createConverter(ZigBeeThingHandler thing, ZigBeeThingChannel channel,
            ZigBeeCoordinatorHandler coordinator) {
        this.thing = thing;
        this.channel = channel;
        this.coordinator = coordinator;

        return true;
    }

    public abstract void initializeConverter();

    public void disposeConverter() {
    }

    /**
     * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node
     * should be sent a message.
     *
     * @param channel the {@link ZigBeeThingChannel}
     */
    public void handleRefresh() {
    }

    /**
     * Receives a command from openHAB and translates it to an operation on the Z-Wave network.
     *
     * @param channel the {@link ZigBeeThingChannel}
     * @param command the {@link Command} to send
     */
    public void handleCommand(Command command) {
    }

    /**
     *
     * @param clusterId
     * @return
     */
    public static ZigBeeConverter getConverter(String clusterId) {
        if (clusterMap == null) {
            clusterMap = new HashMap<String, Class<? extends ZigBeeConverter>>();

            clusterMap.put("OnOff", ZigBeeOnOffSwitchConverter.class);
            clusterMap.put("Level", ZigBeeLevelConverter.class);
            clusterMap.put("Color", ZigBeeColorConverter.class);
            clusterMap.put("ColorTemperature", ZigBeeColorTemperatureConverter.class);
            clusterMap.put("Occupancy", ZigBeeOccupancyConverter.class);
            clusterMap.put("Temperature", ZigBeeTemperatureConverter.class);
        }

        Constructor<? extends ZigBeeConverter> constructor;
        try {
            if (clusterMap.get(clusterId) == null) {
                logger.warn("Cluster converter {} is not implemented!", String.format("%04X", clusterId));
                return null;
            }
            constructor = clusterMap.get(clusterId).getConstructor();
            return constructor.newInstance();
        } catch (

        Exception e) {
            // logger.error("Command processor error");
        }

        return null;
    }

    protected void updateChannelState(State state) {
        thing.setChannelState(channel.getUID(), state);
    }

}
