package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;
import java.util.Enumeration;

import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.ZigBeeApiConstants;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Reporter;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.OccupancySensing;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeOccupancySensorConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private Attribute attrOccupancy;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrOccupancy = coordinator.openAttribute(channel.getAddress(), OccupancySensing.class,
                Attributes.CURRENT_LEVEL, this);
        if (attrOccupancy == null) {
            logger.error("Error opening attribute {}", channel.getAddress());
            return;
        }

        final Device device = coordinator.getDevice(channel.getAddress());
        final Reporter reporter = device.getCluster(ZigBeeApiConstants.CLUSTER_ID_OCCUPANCY_SENSING).getAttribute(0)
                .getReporter();
        if (reporter != null) {
            logger.debug("{} Reporting configured", channel.getAddress());
            reporter.addReportListener(this, false);
        }

        try {
            Integer value = (Integer) attrOccupancy.getValue();
            if ((value & 0x01) == 1) {
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

    }

    @Override
    public void handleRefresh() {

    }

    @Override
    public void handleCommand(Command command) {
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        final Enumeration<Attribute> attributes = reports.keys();
        while (attributes.hasMoreElements()) {
            final Attribute attribute = attributes.nextElement();

            // Make sure this is the right attribute!
            if (clusterId != ZigBeeApiConstants.CLUSTER_ID_OCCUPANCY_SENSING && attribute.getId() != 0) {
                continue;
            }

            final Integer value = (Integer) reports.get(attribute);
            State state = OnOffType.OFF;
            if ((value & 0x01) == 1) {
                state = OnOffType.ON;
            }
            logger.debug("{} ZigBee attribute report: {} {} ({}) is {} ({})", endPointId, clusterId,
                    attribute.getName(), attribute.getId(), value, state);
            updateChannelState(state);
        }
    }
}
