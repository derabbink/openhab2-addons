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
import org.bubblecloud.zigbee.api.cluster.impl.attribute.AttributeDescriptor;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.bubblecloud.zigbee.api.cluster.measurement_sensing.ElectricalMeasurement;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeElectricalMeasurementConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private Attribute attrMeasurement;

    private boolean initialised = false;
    private double scale = 1.0;

    enum ELECTRICAL_PARAMETERS {
        ACFrequency,
        RMSVoltage,
        RMSCurrent,
        ActivePower,
        ReactivePower,
        ApparentPower,
        PowerFactor;
    }

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        if (channel.getArguments().containsKey("Scale")) {
            scale = Double.parseDouble(channel.getArguments().get("Scale"));
        }

        ELECTRICAL_PARAMETERS parameter = ELECTRICAL_PARAMETERS.valueOf(channel.getArguments().get("Parameter"));

        AttributeDescriptor attribute;
        switch (parameter) {
            case ACFrequency:
                attribute = Attributes.AC_FREQUENCY;
                break;
            case ActivePower:
                attribute = Attributes.ACTIVE_POWER;
                break;
            case ApparentPower:
                attribute = Attributes.APPARENT_POWER;
                break;
            case PowerFactor:
                attribute = Attributes.POWER_FACTOR;
                break;
            case RMSCurrent:
                attribute = Attributes.RMS_CURRENT;
                break;
            case RMSVoltage:
                attribute = Attributes.RMS_VOLTAGE;
                break;
            case ReactivePower:
                attribute = Attributes.REACTIVE_POWER;
                break;
            default:
                logger.error("Error unknown parameter {}", channel.getArguments().get("Parameter"));
                return;
        }

        attrMeasurement = coordinator.openAttribute(channel.getAddress(), ElectricalMeasurement.class, attribute, this);
        if (attrMeasurement == null) {
            logger.error("Error opening attribute {}", channel.getAddress());
            return;
        }

        initialised = true;

        final Device device = coordinator.getDevice(channel.getAddress());
        if (device == null) {
            logger.warn("{}: Device not found at {}.", channel.getUID(), channel.getAddress());
            return;
        }
        Cluster cluster = device.getCluster(ZigBeeApiConstants.CLUSTER_ID_ELECTRICAL_MEASUREMENT);
        if (cluster != null) {
            Attribute attr = cluster.getAttribute(attrMeasurement.getId());
            final Reporter reporter = attr.getReporter();
            if (reporter == null) {
                logger.warn("{}: Attribute does not provide reports.", channel.getUID());
            } else {
                reporter.addReportListener(this, false);
            }
        }

        try {
            Integer value = (Integer) attrMeasurement.getValue();
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
