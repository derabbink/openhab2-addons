/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.util.Dictionary;
import java.util.Set;

import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.ColorControl;
import org.bubblecloud.zigbee.api.cluster.general.LevelControl;
import org.bubblecloud.zigbee.api.cluster.general.OnOff;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class ZigBeeLightHandler extends BaseThingHandler implements ZigBeeEventListener, ReportListener {

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Sets.newHashSet(
            ZigBeeBindingConstants.THING_TYPE_ON_OFF_LIGHT, ZigBeeBindingConstants.THING_TYPE_DIMMABLE_LIGHT,
            ZigBeeBindingConstants.THING_TYPE_COLOR_DIMMABLE_LIGHT);

    private static final int DIM_STEPSIZE = 30;

    private String lightAddress;
    private Attribute attrOnOff;
    private Attribute attrLevel;
    private Attribute attrHue;
    private Attribute attrSaturation;
    private OnOff clusOnOff;
    private LevelControl clusLevel;
    private Attribute attrColorTemp;
    private ColorControl clusColor;

    private OnOffType currentOnOff = OnOffType.OFF;
    private HSBType currentHSB = new HSBType(new DecimalType(0), new PercentType(0), PercentType.HUNDRED);

    private Logger logger = LoggerFactory.getLogger(ZigBeeEventListener.class);

    private ZigBeeCoordinatorHandler coordinatorHandler;

    public ZigBeeLightHandler(Thing zigbeeDevice) {
        super(zigbeeDevice);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee light handler.");
        final String configAddress = (String) getConfig().get(ZigBeeBindingConstants.PARAMETER_MACADDRESS);
        lightAddress = configAddress;
        attrOnOff = null;
        attrLevel = null;
        attrHue = null;
        attrSaturation = null;
        attrColorTemp = null;
        clusOnOff = null;
        clusLevel = null;
        clusColor = null;
    }

    @Override
    protected void bridgeHandlerInitialized(ThingHandler thingHandler, Bridge bridge) {
        coordinatorHandler = (ZigBeeCoordinatorHandler) thingHandler;

        if (lightAddress != null) {
            if (coordinatorHandler != null) {
                coordinatorHandler.subscribeEvents(lightAddress, this);
                // getThing().setStatus(getBridge().getStatus());
            }
        }
        // until we get an update put the Thing offline
        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposes. Unregistering listener.");
        if (lightAddress != null) {
            if (coordinatorHandler != null) {
                coordinatorHandler.unsubscribeEvents(lightAddress, this);
            }
            lightAddress = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (coordinatorHandler == null) {
            logger.warn("Coordinator handler not found. Cannot handle command without coordinator.");
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        switch (channelUID.getId()) {
            case ZigBeeBindingConstants.CHANNEL_SWITCH:
                if (command instanceof PercentType) {
                    if (((PercentType) command).intValue() == 0) {
                        currentOnOff = OnOffType.OFF;
                    } else {
                        currentOnOff = OnOffType.ON;
                    }
                } else if (command instanceof OnOffType) {
                    currentOnOff = (OnOffType) command;
                }
                commandOnOff(currentOnOff);
                break;

            case ZigBeeBindingConstants.CHANNEL_BRIGHTNESS:
                int level = 0;
                if (command instanceof PercentType) {
                    level = ((PercentType) command).intValue();
                } else if (command instanceof OnOffType) {
                    if ((OnOffType) command == OnOffType.ON) {
                        level = 100;
                    } else {
                        level = 0;
                    }
                }
                commandLevel(level);
                break;

            case ZigBeeBindingConstants.CHANNEL_COLOR:
                if (command instanceof HSBType) {
                    currentHSB = new HSBType(command.toString());
                } else if (command instanceof PercentType) {
                    currentHSB = new HSBType(currentHSB.getHue(), (PercentType) command, PercentType.HUNDRED);
                } else if (command instanceof OnOffType) {
                    PercentType saturation;
                    if ((OnOffType) command == OnOffType.ON) {
                        saturation = PercentType.HUNDRED;
                    } else {
                        saturation = PercentType.ZERO;
                    }
                    currentHSB = new HSBType(currentHSB.getHue(), saturation, PercentType.HUNDRED);
                }
                commandColor(currentHSB);
                break;
            case ZigBeeBindingConstants.CHANNEL_COLORTEMPERATURE:
                PercentType colorTemp = PercentType.ZERO;
                if (command instanceof PercentType) {
                    colorTemp = (PercentType) command;
                } else if (command instanceof OnOffType) {
                    if ((OnOffType) command == OnOffType.ON) {
                        colorTemp = PercentType.HUNDRED;
                    } else {
                        colorTemp = PercentType.ZERO;
                    }
                }
                commandColorTemp(colorTemp);
                break;
        }
    }

    @Override
    public boolean openDevice() {
        attrOnOff = coordinatorHandler.openAttribute(lightAddress, OnOff.class, Attributes.ON_OFF, this);
        clusOnOff = coordinatorHandler.openCluster(lightAddress, OnOff.class);
        if (attrOnOff == null || clusOnOff == null) {
            logger.error("Error opening device on/off controls {}", lightAddress);
            return false;
        }

        if (this.getThing().getThingTypeUID().equals(ZigBeeBindingConstants.THING_TYPE_ON_OFF_LIGHT) == false) {
            attrLevel = coordinatorHandler.openAttribute(lightAddress, LevelControl.class, Attributes.CURRENT_LEVEL,
                    this);
            clusLevel = coordinatorHandler.openCluster(lightAddress, LevelControl.class);
            if (attrLevel == null || clusLevel == null) {
                logger.error("Error opening device level controls {}", lightAddress);
                return false;
            }
        }

        if (this.getThing().getThingTypeUID().equals(ZigBeeBindingConstants.THING_TYPE_COLOR_DIMMABLE_LIGHT)) {
            attrHue = coordinatorHandler.openAttribute(lightAddress, ColorControl.class, Attributes.CURRENT_HUE, null);
            attrSaturation = coordinatorHandler.openAttribute(lightAddress, ColorControl.class,
                    Attributes.CURRENT_SATURATION, null);
            attrColorTemp = coordinatorHandler.openAttribute(lightAddress, ColorControl.class,
                    Attributes.COLOR_TEMPERATURE, null);
            clusColor = coordinatorHandler.openCluster(lightAddress, ColorControl.class);
            if (attrHue == null || attrSaturation == null || attrColorTemp == null || clusColor == null) {
                logger.error("Error opening device color controls {}", lightAddress);
                return false;
            }
        }
        return true;
    }

    @Override
    public void closeDevice() {
        updateStatus(ThingStatus.OFFLINE);

        coordinatorHandler.closeAttribute(attrOnOff, this);
        coordinatorHandler.closeAttribute(attrLevel, this);
        coordinatorHandler.closeAttribute(attrHue, null);
        coordinatorHandler.closeAttribute(attrSaturation, null);
        coordinatorHandler.closeAttribute(attrColorTemp, null);
        coordinatorHandler.closeCluster(clusOnOff);
        coordinatorHandler.closeCluster(clusLevel);
        coordinatorHandler.closeCluster(clusColor);
    }

    @Override
    public void onEndpointStateChange() {
        try {
            if (attrOnOff != null) {
                updateStateOnOff((boolean) attrOnOff.getValue());
            }
            if (attrLevel != null) {
                updateStateLevel((int) attrLevel.getValue());
            }
            if (attrHue != null && attrSaturation != null) {
                updateStateColor((int) attrHue.getValue(), (int) attrSaturation.getValue());
            }
            if (attrColorTemp != null) {
                updateStateColorTemp((int) attrColorTemp.getValue());
            }
            // Device is now online
            updateStatus(ThingStatus.ONLINE);
        } catch (ZigBeeClusterException e) {
            updateStatus(ThingStatus.OFFLINE);
            e.printStackTrace();
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);
        if (attrOnOff != null) {
            Object value = reports.get(attrOnOff);
            if (value != null) {
                updateStateOnOff((boolean) value);
            }
        }
        if (attrLevel != null) {
            Object value = reports.get(attrLevel);
            if (value != null) {
                updateStateLevel((int) value);
            }
        }

        updateStatus(ThingStatus.ONLINE);
    }

    public void commandOnOff(OnOffType state) {
        if (clusOnOff == null) {
            return;
        }
        try {
            if (state == OnOffType.ON) {
                clusOnOff.on();
            } else {
                clusOnOff.off();
            }
            updateStatus(ThingStatus.ONLINE);
        } catch (ZigBeeDeviceException e) {
            updateStatus(ThingStatus.OFFLINE);
            e.printStackTrace();
        }
    }

    private void updateStateOnOff(boolean onOff) {
        currentOnOff = onOff == true ? OnOffType.ON : OnOffType.OFF;
        updateState(new ChannelUID(getThing().getUID(), ZigBeeBindingConstants.CHANNEL_SWITCH), currentOnOff);
    }

    private void commandLevel(int state) {
        if (clusLevel == null) {
            return;
        }
        try {
            clusLevel.moveToLevelWithOnOff((short) (state * 254.0 / 100.0 + 0.5), 10);
            updateStatus(ThingStatus.ONLINE);
        } catch (ZigBeeDeviceException e) {
            updateStatus(ThingStatus.OFFLINE);
            e.printStackTrace();
        }
    }

    private void updateStateLevel(int level) {
        PercentType chanPercent;
        if (currentOnOff == OnOffType.OFF) {
            level = 0;
        }
        chanPercent = new PercentType((int) (level * 100.0 / 254.0 + 0.5));
        updateState(new ChannelUID(getThing().getUID(), ZigBeeBindingConstants.CHANNEL_BRIGHTNESS), chanPercent);
    }

    public void commandColor(HSBType state) {
        if (clusColor == null) {
            return;
        }

        try {
            int hue = state.getHue().intValue();
            int saturation = state.getSaturation().intValue();
            clusColor.moveToHue((int) (hue * 254.0 / 360.0 + 0.5), 0, 10);
            clusColor.movetoSaturation((int) (saturation * 254.0 / 100.0 + 0.5), 10);
            clusLevel.moveToLevelWithOnOff((short) (state.getBrightness().intValue() * 254.0 / 100.0 + 0.5), 10);
            updateStatus(ThingStatus.ONLINE);
        } catch (ZigBeeDeviceException e) {
            updateStatus(ThingStatus.OFFLINE);
            e.printStackTrace();
        }
    }

    private void updateStateColor(int hue, int saturation) {
        currentHSB = new HSBType(new DecimalType(hue * 360.0 / 254.0 + 0.5),
                new PercentType((int) (saturation * 100.0 / 254.0 + 0.5)), PercentType.HUNDRED);
        PercentType statePercent = new PercentType((int) (saturation * 100.0 / 254.0 + 0.5));
        updateState(new ChannelUID(getThing().getUID(), ZigBeeBindingConstants.CHANNEL_COLOR), currentHSB);
        updateState(new ChannelUID(getThing().getUID(), ZigBeeBindingConstants.CHANNEL_COLOR), statePercent);
    }

    public void commandColorTemp(PercentType state) {
        if (clusColor == null) {
            return;
        }

        // Range of 2000K to 6500K, gain = 4500K, offset = 2000K
        double kelvin = state.intValue() * 4500.0 / 100.0 + 2000.0;
        try {
            clusColor.moveToColorTemperature((short) (1e6 / kelvin + 0.5), 10);
            updateStatus(ThingStatus.ONLINE);
        } catch (ZigBeeDeviceException e) {
            updateStatus(ThingStatus.OFFLINE);
            e.printStackTrace();
        }
    }

    private void updateStateColorTemp(int colorTemp) {
        // Range of 2000K to 6500K, gain = 4500K, offset = 2000K
        int value = (int) (((1e6 / colorTemp) - 2000.0) / 4000.0 * 100.0 + 0.5);
        if (value < 0) {
            value = 0;
        } else if (value > 100) {
            value = 100;
        }
        PercentType state = new PercentType(value);
        updateState(new ChannelUID(getThing().getUID(), ZigBeeBindingConstants.CHANNEL_COLORTEMPERATURE), state);
    }

    /*
     * @Override public void onLightRemoved(HueBridge bridge, FullLight light) {
     * 
     * if (light.getId().equals(lightAddress)) {
     * getThing().setStatus(ThingStatus.OFFLINE); } }
     * 
     * @Override public void onLightAdded(HueBridge bridge, FullLight light) {
     * if (light.getId().equals(lightAddress)) {
     * getThing().setStatus(ThingStatus.ONLINE); onLightStateChanged(bridge,
     * light); } }
     */

}
