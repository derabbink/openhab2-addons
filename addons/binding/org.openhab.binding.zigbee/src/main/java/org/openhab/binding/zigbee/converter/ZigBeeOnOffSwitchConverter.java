package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.OnOff;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeOnOffSwitchConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private OnOffType currentOnOff = OnOffType.OFF;
    private Attribute attrOnOff;
    private OnOff clusOnOff;

    private boolean initialised = false;

    private void initialise() {
        if (initialised == true) {
            return;
        }

        attrOnOff = coordinator.openAttribute(channel.getAddress(), OnOff.class, Attributes.ON_OFF, this);
        clusOnOff = coordinator.openCluster(channel.getAddress(), OnOff.class);
        if (attrOnOff == null || clusOnOff == null) {
            logger.error("Error opening device on/off controls {}", channel.getAddress());
        }

        try {
            Object value = attrOnOff.getValue();
            if (value != null && (boolean) value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }

        initialised = true;
    }

    @Override
    public void disposeConverter() {
        if (attrOnOff != null) {
            coordinator.closeAttribute(attrOnOff, this);
        }
        if (clusOnOff != null) {
            coordinator.closeCluster(clusOnOff);
        }
    }

    @Override
    public void handleRefresh() {
        try {
            Object value = attrOnOff.getValue();
            if (value != null && (boolean) value == true) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleCommand(Command command) {
        initialise();

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
