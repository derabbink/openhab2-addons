package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.OnOff;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler;
import org.openhab.binding.zigbee.handler.ZigBeeThingHandler.ZigBeeThingChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeOnOffSwitchConverter extends ZigBeeClusterConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeClusterConverter.class);

    private OnOffType currentOnOff = OnOffType.OFF;
    private Attribute attrOnOff;
    private OnOff clusOnOff;

    @Override
    public boolean initializeConverter(ZigBeeThingHandler thing, ZigBeeThingChannel channel,
            ZigBeeCoordinatorHandler coordinator) {
        super.initializeConverter(thing, channel, coordinator);

        // attrOnOff = coordinator.openAttribute(channel.getAddress(), OnOff.class, Attributes.ON_OFF, this);
        clusOnOff = coordinator.openCluster(channel.getAddress(), OnOff.class);
        if (attrOnOff == null || clusOnOff == null) {
            logger.error("Error opening device on/off controls {}", channel.getAddress());
            return false;
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        // coordinator.closeAttribute(attrOnOff, this);
        coordinator.closeCluster(clusOnOff);
    }

    @Override
    public void handleRefresh() {
    }

    @Override
    public State handleEvent(Dictionary<Attribute, Object> reports) {
        return null;
    }

    @Override
    public void handleCommand(Command command) {
        if (command instanceof PercentType) {
            if (((PercentType) command).intValue() == 0) {
                currentOnOff = OnOffType.OFF;
            } else {
                currentOnOff = OnOffType.ON;
            }
        } else if (command instanceof OnOffType) {
            currentOnOff = (OnOffType) command;
        }

        if (clusOnOff == null) {
            return;
        }
        try {
            if (currentOnOff == OnOffType.ON) {
                clusOnOff.on();
            } else {
                clusOnOff.off();
            }
        } catch (ZigBeeDeviceException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);
        if (attrOnOff != null) {
            Object value = reports.get(attrOnOff);
            if (value != null && (boolean) value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }

}
