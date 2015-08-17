/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.network.ZigBeeNode;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.internal.ZigBeeConfigProvider;
import org.openhab.binding.zigbee.internal.ZigBeeProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeDiscoveryService} tracks ZigBee devices which are associated
 * to coordinator.
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDiscoveryService.class);

    private final static int SEARCH_TIME = 60;

    private ZigBeeCoordinatorHandler coordinatorHandler;

    public ZigBeeDiscoveryService(ZigBeeCoordinatorHandler coordinatorHandler) {
        super(SEARCH_TIME);
        this.coordinatorHandler = coordinatorHandler;
    }

    public void activate() {
        logger.debug("Activating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());

        // Listen for device events
        // coordinatorHandler.addDeviceListener(this);

        // startScan();
    }

    @Override
    public void deactivate() {
        logger.debug("Deactivating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());

        // Remove the listener
        // coordinatorHandler.removeDeviceListener(this);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return ZigBeeBindingConstants.SUPPORTED_DEVICE_TYPES_UIDS;
    }

    @Override
    public void startScan() {
        logger.debug("Starting ZigBee scan for {}", coordinatorHandler.getThing().getUID());

        // Start the search for new devices
        coordinatorHandler.startDeviceDiscovery();
    }

    public void addThing(ZigBeeNode node, List<Device> devices, String manufacturer, String model) {
        // Try and find this product in the database
        ZigBeeProduct foundProduct = null;
        for (ZigBeeProduct product : ZigBeeConfigProvider.getProductIndex()) {
            if (product.match(manufacturer, model) == true) {
                foundProduct = product;
                break;
            }
        }

        // Did we find it?
        if (foundProduct == null) {
            // No - the device is unknown to us
            // We need to dynamically create the thing - when this functionality is included in ESH!

            // For now just return
            return;
        }

        ThingUID bridgeUID = coordinatorHandler.getThing().getUID();
        String thingId = node.getIeeeAddress().toLowerCase().replaceAll("[^a-z0-9_/]", "");
        ThingUID thingUID = new ThingUID(foundProduct.getThingTypeUID(), bridgeUID, thingId);

        String label = null;
        if (manufacturer != null && model != null) {
            label = manufacturer.toString().trim() + " " + model.toString().trim();
        } else {
            label = "Unknown ZigBee Device " + node.getIeeeAddress();
        }

        Map<String, Object> properties = new HashMap<>(1);
        properties.put(ZigBeeBindingConstants.PARAMETER_MACADDRESS, node.getIeeeAddress());
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withBridge(bridgeUID).withLabel(label).build();

        thingDiscovered(discoveryResult);
    }
    /*
     * public void deviceAdded(Device device, String description) {
     * logger.debug("Device discovery: '{}' {} {}", device.getDeviceType(), device.getEndpointId(),
     * device.getProfileId());
     *
     * ThingUID thingUID = getThingUID(device);
     * if (thingUID != null) {
     * String label = device.getDeviceType();
     * if (description != null) {
     * label += "(" + description + ")";
     * }
     * ThingUID bridgeUID = coordinatorHandler.getThing().getUID();
     * Map<String, Object> properties = new HashMap<>(1);
     * properties.put(ZigBeeBindingConstants.PARAMETER_MACADDRESS, device.getEndpointId());
     * DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
     * .withBridge(bridgeUID).withLabel(label).build();
     *
     * thingDiscovered(discoveryResult);
     * } else {
     * logger.info("Discovered unknown device type '{}' at address {}", device.getDeviceType(),
     * device.getEndpointId());
     * }
     * }
     */

    /*
     * public void deviceUpdated(Device device) {
     * // Nothing to do here!
     * }
     *
     * public void deviceRemoved(Device device) {
     * ThingUID thingUID = getThingUID(device);
     *
     * if (thingUID != null) {
     * thingRemoved(thingUID);
     * }
     * }
     */
    @Override
    protected void startBackgroundDiscovery() {
    }

}
