package org.openhab.binding.zigbee.converter;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.ZigBeeDeviceException;
import org.bubblecloud.zigbee.api.cluster.general.ColorControl;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.Attributes;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeColorTemperatureConverter extends ZigBeeConverter implements ReportListener {
    private Logger logger = LoggerFactory.getLogger(ZigBeeConverter.class);

    private Attribute attrColorTemp;
    private ColorControl clusColor;

    private boolean initialised = false;

    private void initialise() {
        if (initialised == true) {
            return;
        }
        attrColorTemp = coordinator.openAttribute(channel.getAddress(), ColorControl.class,
                Attributes.COLOR_TEMPERATURE, null);
        clusColor = coordinator.openCluster(channel.getAddress(), ColorControl.class);
        if (attrColorTemp == null || clusColor == null) {
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
        initialise();

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

        // Range of 2000K to 6500K, gain = 4500K, offset = 2000K
        double kelvin = colorTemp.intValue() * 4500.0 / 100.0 + 2000.0;
        try {
            clusColor.moveToColorTemperature((short) (1e6 / kelvin + 0.5), 10);
        } catch (ZigBeeDeviceException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receivedReport(String endPointId, short clusterId, Dictionary<Attribute, Object> reports) {
        logger.debug("ZigBee attribute reports {} from {}", reports, endPointId);

    }

}
