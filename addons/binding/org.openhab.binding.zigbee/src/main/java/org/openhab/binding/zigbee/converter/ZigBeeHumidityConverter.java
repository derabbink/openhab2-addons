package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;
import java.util.Enumeration;

import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.ZigBeeApiConstants;
import org.bubblecloud.zigbee.api.cluster.Cluster;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Reporter;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.RelativeHumidityMeasurement;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeHumidityConverter extends ZigBeeConverter implements ReportListener {
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

        // TODO this works, but I think uses the wrong concept!
        attrTemperature = coordinator.openAttribute(channel.getAddress(), RelativeHumidityMeasurement.class,
                Attributes.CURRENT_LEVEL, this);
        if (attrTemperature == null) {
            logger.error("Error opening attribute {}", channel.getAddress());
            return;
        }

        initialised = true;

        final Device device = coordinator.getDevice(channel.getAddress());
        if (device == null) {
            logger.warn("{}: Device not found at {}.", channel.getUID(), channel.getAddress());
            return;
        }
        Cluster cluster = device.getCluster(ZigBeeApiConstants.CLUSTER_ID_RELATIVE_HUMIDITY_MEASUREMENT);
        if (cluster != null) {
            Attribute attribute = cluster.getAttribute(0);
            final Reporter reporter = attribute.getReporter();
            if (reporter == null) {
                logger.warn("{}: Attribute does not provide reports.", channel.getUID());
            } else {
                reporter.addReportListener(this, false);
            }
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
        final Enumeration<Attribute> attributes = reports.keys();
        while (attributes.hasMoreElements()) {
            final Attribute attribute = attributes.nextElement();
            final Integer value = (Integer) reports.get(attribute);
            if (value != null) {
                double dValue = (double) value * scale;
                updateChannelState(new DecimalType(dValue));
            }
        }
    }

}
