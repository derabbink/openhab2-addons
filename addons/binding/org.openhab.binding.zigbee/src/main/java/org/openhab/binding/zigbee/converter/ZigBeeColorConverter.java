package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.ColorControl;
import org.bubblecloud.zigbee.api.cluster.general.LevelControl;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeColorConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private HSBType currentHSB = new HSBType(new DecimalType(0), new PercentType(0), PercentType.HUNDRED);
    private Attribute attrLevel;
    private Attribute attrHue;
    private Attribute attrSaturation;
    private ColorControl clusColor;
    private LevelControl clusLevel;

    private boolean initialised = false;

    @Override
    public void initializeConverter() {
        if (initialised == true) {
            return;
        }

        attrLevel = coordinator.openAttribute(channel.getAddress(), LevelControl.class, Attributes.CURRENT_LEVEL, this);
        clusLevel = coordinator.openCluster(channel.getAddress(), LevelControl.class);
        if (attrLevel == null || clusLevel == null) {
            logger.error("Error opening device level controls {}", channel.getAddress());
            return;
        }

        attrHue = coordinator.openAttribute(channel.getAddress(), ColorControl.class, Attributes.CURRENT_HUE, null);
        attrSaturation = coordinator.openAttribute(channel.getAddress(), ColorControl.class,
                Attributes.CURRENT_SATURATION, null);
        clusColor = coordinator.openCluster(channel.getAddress(), ColorControl.class);
        if (attrHue == null || attrSaturation == null || clusColor == null) {
            logger.error("Error opening device color controls {}", channel.getAddress());
            return;
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
        if (initialised == false) {
            return;
        }

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

        try {
            int hue = currentHSB.getHue().intValue();
            int saturation = currentHSB.getSaturation().intValue();
            clusColor.moveToHue((int) (hue * 254.0 / 360.0 + 0.5), 0, 10);
            clusColor.movetoSaturation((int) (saturation * 254.0 / 100.0 + 0.5), 10);
            clusLevel.moveToLevelWithOnOff((short) (currentHSB.getBrightness().intValue() * 254.0 / 100.0 + 0.5), 10);
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
