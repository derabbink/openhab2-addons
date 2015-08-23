package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.cluster.general.LevelControl;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeOccupancySensorConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private Attribute attrTemperature;

    private boolean initialised = false;
    private double scale = 1.0;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        if (channel.getArguments().containsKey("Scale")) {
            scale = Double.parseDouble(channel.getArguments().get("Scale"));
        }

        attrTemperature = coordinator.openAttribute(channel.getAddress(), LevelControl.class, Attributes.CURRENT_LEVEL,
                this);
        if (attrTemperature == null) {
            logger.error("Error opening attribute {}", channel.getAddress());
            return;
        }

        try {
            Integer value = (Integer) attrTemperature.getValue();
            if (value != null) {
                double dValue = (double) value * scale;
                updateChannelState(new DecimalType(dValue));
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
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {

        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);
        if (attrTemperature != null) {
            try {
                Integer value = (Integer) attrTemperature.getValue();
                if (value != null) {
                    double dValue = (double) value * scale;
                    updateChannelState(new DecimalType(dValue));
                }
            } catch (ZigBeeClusterException e) {
                e.printStackTrace();
            }
        }
    }

}
