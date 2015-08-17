package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.LevelControl;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeLevelConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private Attribute attrLevel;
    private LevelControl clusLevel;

    private boolean initialised = false;

    private void initialise() {
        if (initialised == true) {
            return;
        }

        attrLevel = coordinator.openAttribute(channel.getAddress(), LevelControl.class, Attributes.CURRENT_LEVEL, this);
        clusLevel = coordinator.openCluster(channel.getAddress(), LevelControl.class);
        if (attrLevel == null || clusLevel == null) {
            logger.error("Error opening device level controls {}", channel.getAddress());
            return;
        }

        try {
            Integer value = (Integer) attrLevel.getValue();
            if (value != null) {
                value = value * 100 / 255;
                if (value > 100) {
                    value = 100;
                }
                updateChannelState(new PercentType(value));
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }

        initialised = true;
    }

    @Override
    public void disposeConverter() {

    }

    @Override
    public void handleRefresh() {

    }

    @Override
    public void handleCommand(Command command) {
        initialise();

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

        try {
            clusLevel.moveToLevelWithOnOff((short) (level * 254.0 / 100.0 + 0.5), 10);
        } catch (ZigBeeDeviceException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);
        if (attrLevel != null) {
            Object value = reports.get(attrLevel);
            if (value != null) {
                // updateChannelState((int) value);
            }
        }
    }

}
