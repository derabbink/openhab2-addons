/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.ZigBeeApiConstants;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeDiscoveryService} tracks ZigBee lights which are associated
 * to coordinator.
 * 
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeDiscoveryService extends AbstractDiscoveryService {
	private final Logger logger = LoggerFactory
			.getLogger(ZigBeeDiscoveryService.class);

	private final static int SEARCH_TIME = 60;

	private ZigBeeCoordinatorHandler coordinatorHandler;
	List<ZigBeeThingType> zigbeeThingTypeList = new ArrayList<ZigBeeThingType>();

	public ZigBeeDiscoveryService(ZigBeeCoordinatorHandler coordinatorHandler) {
		super(SEARCH_TIME);
		this.coordinatorHandler = coordinatorHandler;

		// The following code adds devices to a list.
		// The device list resolves Thing names from supported clusters
		// The discovery code will search through this list to find the
		// best matching cluster list and use the name for this Thing.
		// A 'best match' is defined as the maximum number of matching clusters.
		zigbeeThingTypeList.add(new ZigBeeThingType(
				"OnOffLight", new int[] {
				ZigBeeApiConstants.CLUSTER_ID_ON_OFF
			}));
		zigbeeThingTypeList.add(new ZigBeeThingType(
				"DimmableLight", new int[] {
				ZigBeeApiConstants.CLUSTER_ID_ON_OFF,
				ZigBeeApiConstants.CLUSTER_ID_LEVEL_CONTROL
			}));
		zigbeeThingTypeList.add(new ZigBeeThingType(
				"ColorDimmableLight", new int[] {
				ZigBeeApiConstants.CLUSTER_ID_ON_OFF,
				ZigBeeApiConstants.CLUSTER_ID_COLOR_CONTROL,
				ZigBeeApiConstants.CLUSTER_ID_LEVEL_CONTROL
			}));

	}

	public void activate() {
		logger.debug("Activating ZigBee discovery service for {}",
				coordinatorHandler.getThing().getUID());

		// Listen for device events
		// coordinatorHandler.addDeviceListener(this);

		// startScan();
	}

	@Override
	public void deactivate() {
		logger.debug("Deactivating ZigBee discovery service for {}",
				coordinatorHandler.getThing().getUID());

		// Remove the listener
		// coordinatorHandler.removeDeviceListener(this);
	}

	@Override
	public Set<ThingTypeUID> getSupportedThingTypes() {
		return ZigBeeBindingConstants.SUPPORTED_DEVICE_TYPES_UIDS;
	}

	@Override
	public void startScan() {
		logger.debug("Starting ZigBee scan for {}", coordinatorHandler
				.getThing().getUID());

		// Start the search for new devices
		coordinatorHandler.startDeviceDiscovery();
	}

	private ThingUID getThingUID(Device device) {
		ThingUID bridgeUID = coordinatorHandler.getThing().getUID();

		int max = 0;
		ZigBeeThingType bestThing = null;
		for(ZigBeeThingType thing : zigbeeThingTypeList) {
			int s = thing.getScore(device);
			if(s > max) {
				max = s;
				bestThing = thing;
			}
		}
		if(bestThing == null) {
			logger.debug("No ThingUID found for device {}, type {} ({})", device.getDescription(), device.getDeviceType(), device.getDeviceTypeId());
			return null;
		}
		logger.debug("Using ThingUID: {}", bestThing.getUID());
		
		// Our thing ID is based on the ZigBee device type - we need to remove
		// spaces
		ThingTypeUID thingTypeUID = new ThingTypeUID(
				ZigBeeBindingConstants.BINDING_ID, bestThing.getUID());

		if (getSupportedThingTypes().contains(thingTypeUID)) {
			String thingId = device.getEndpointId().toLowerCase().replaceAll("[^a-z0-9_/]", "").replaceAll("/", "_");
			ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);
			return thingUID;
		} else {
			return null;
		}
	}

	public void deviceAdded(Device device, String description) {
		logger.debug("Device discovery: '{}' {} {}", device.getDeviceType(),
				device.getEndpointId(), device.getProfileId());

		ThingUID thingUID = getThingUID(device);
		if (thingUID != null) {
			String label = device.getDeviceType();
			if (description != null) {
				label += "(" + description + ")";
			}
			ThingUID bridgeUID = coordinatorHandler.getThing().getUID();
			Map<String, Object> properties = new HashMap<>(1);
			properties.put(ZigBeeBindingConstants.PARAMETER_MACADDRESS,
					device.getEndpointId());
			DiscoveryResult discoveryResult = DiscoveryResultBuilder
					.create(thingUID).withProperties(properties)
					.withBridge(bridgeUID).withLabel(label).build();

			thingDiscovered(discoveryResult);
		} else {
			logger.info("Discovered unknown device type '{}' at address {}",
					device.getDeviceType(), device.getEndpointId());
		}
	}

	public void deviceUpdated(Device device) {
		// Nothing to do here!
	}

	public void deviceRemoved(Device device) {
		ThingUID thingUID = getThingUID(device);

		if (thingUID != null) {
			thingRemoved(thingUID);
		}
	}

	@Override
	protected void startBackgroundDiscovery() {
	}

	private class ZigBeeThingType {
		private String label;
		private List<Integer> clusters;

		private ZigBeeThingType(String label, int clusters[]) {
			this.label = label;
			this.clusters = new ArrayList<Integer>();
			for (int i = 0; i < clusters.length; i++) {
				this.clusters.add(clusters[i]);
			} 
		}

		/**
		 * Return a count of how many many clusters this thing type
		 * supports in the device 
		 * @param device
		 * @return
		 */
		public int getScore(Device device) {
			int score = 0;

			for (int c : device.getInputClusters()) {
            	if(clusters.contains(c)) {
            		score++;
            	}
            }
			return score;
		}
		
		public String getUID() {
			return label;
		}
	}
}
