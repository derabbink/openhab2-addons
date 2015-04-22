/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.PARAMETER_CHANNEL;
import static org.openhab.binding.zigbee.ZigBeeBindingConstants.PARAMETER_PANID;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

import org.bubblecloud.zigbee.ZigBeeApi;
import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.DeviceListener;
import org.bubblecloud.zigbee.api.cluster.Cluster;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.AttributeDescriptor;
import org.bubblecloud.zigbee.network.model.DiscoveryMode;
import org.bubblecloud.zigbee.network.port.ZigBeePort;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zigbee.discovery.ZigBeeDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeCoordinatorHandler} is responsible for handling commands,
 * which are sent to one of the channels.
 * 
 * @author Chris Jackson - Initial contribution
 */
public abstract class ZigBeeCoordinatorHandler extends BaseBridgeHandler
		implements DeviceListener {
	protected int panId;
	protected int channelId;

	protected ZigBeeApi zigbeeApi;
	private ScheduledFuture<?> pollingJob;
	
	private ZigBeeDiscoveryService discoveryService;

	private ConcurrentMap<String, ZigBeeEventListener> eventListeners = new ConcurrentHashMap<String, ZigBeeEventListener>();

	private Logger logger = LoggerFactory
			.getLogger(ZigBeeCoordinatorHandler.class);

	public ZigBeeCoordinatorHandler(Bridge coordinator) {
		super(coordinator);
	}

	protected void subscribeEvents(String macAddress,
			ZigBeeEventListener handler) {
		eventListeners.put(macAddress, handler);
	}

	protected void unsubscribeEvents(String macAddress,
			ZigBeeEventListener handler) {
		eventListeners.remove(macAddress, handler);
	}

	@Override
	public void initialize() {
		logger.debug("Initializing ZigBee coordinator.");

		panId = ((BigDecimal)getConfig().get(PARAMETER_PANID)).intValue();
		channelId = ((BigDecimal)getConfig().get(PARAMETER_CHANNEL)).intValue();
		//panId = Integer.parseInt((String) getConfig().get(PARAMETER_PANID));
		//channelId = Integer.parseInt((String) getConfig()
		//		.get(PARAMETER_CHANNEL));

		super.initialize();
	}

	@Override
	public void dispose() {
		// Remove the discovery service
		discoveryService.deactivate();

		// Shut down the ZigBee library
		zigbeeApi.shutdown();
		logger.debug("ZigBee network closed.");
	}

	@Override
	protected void updateStatus(ThingStatus status, ThingStatusDetail detail, String desc) {
		super.updateStatus(status, detail, desc);
		for (Thing child : getThing().getThings()) {
			child.setStatusInfo(new ThingStatusInfo(status, detail, desc));
		}
	}
	
	/**
	 * Common initialisation point for all ZigBee coordinators.
	 * Called by bridges after they have initialised their interfaces.
	 * @param networkInterface a ZigBeePort interface instance
	 */
	protected void initializeZigBee(ZigBeePort networkInterface) {
        final EnumSet<DiscoveryMode> discoveryModes = DiscoveryMode.ALL;

        zigbeeApi = new ZigBeeApi(networkInterface, panId, channelId, false, discoveryModes);
        if (!zigbeeApi.startup()) {
            logger.error("Unable to start ZigBee network");
            
            // TODO: Close the network!
            
            
        } else {
            logger.debug("ZigBee network started");
            
            waitForNetwork();
        }
	}

	/**
	 * Called after initial browsing is complete. At this point we're good to go
	 */
	protected void browsingComplete() {
		logger.debug("ZigBee network READY. Found "
				+ zigbeeApi.getDevices().size() + " nodes.");

		final List<Device> devices = zigbeeApi.getDevices();
		for (int i = 0; i < devices.size(); i++) {
			final Device device = devices.get(i);
			logger.debug("ZigBee '{}' device at address {}",
					device.getDeviceType(), device.getEndpointId());

			addNewDevice(device);
		}

		// Add a listener for any new devices
		zigbeeApi.addDeviceListener(this);
	}

	/**
	 * Wait for the network initialisation to complete.
	 */
	protected void waitForNetwork() {
		// Start the discovery service
        discoveryService = new ZigBeeDiscoveryService(this);
        discoveryService.activate();

        // And register it as an OSGi service
        bundleContext.registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>());

		logger.debug("Browsing ZigBee network ...");
		Thread thread = new Thread() {
			public void run() {
				while (!zigbeeApi.isInitialBrowsingComplete()) {
					try {
						Thread.sleep(250);
					} catch (InterruptedException e) {
						break;
					}
				}

				browsingComplete();
			}
		};

		// Kick off the discovery
		thread.start();
	}

	private Device getDeviceByIndexOrEndpointId(ZigBeeApi zigbeeApi,
			String deviceIdentifier) {
		Device device;
		device = zigbeeApi.getDevice(deviceIdentifier);
		if (device == null) {
			logger.debug("Error finding ZigBee device with address {}",
					deviceIdentifier);
		}
		return device;
	}

	public Object attributeRead(String zigbeeAddress, int clusterId, int attributeIndex) {
		final Device device = getDeviceByIndexOrEndpointId(zigbeeApi,
				zigbeeAddress);
		if (device == null) {
			return null;
		}

		return readAttribute(device, clusterId, attributeIndex);
	}
	
	
	public Object readAttribute(Device device, int clusterId, int attributeIndex) {
		final Cluster cluster = device.getCluster(clusterId);
		if (cluster == null) {
			logger.debug("Cluster not found.");
			return null;
		}

		final Attribute attribute = cluster.getAttributes()[attributeIndex];
		if (attribute == null) {
			logger.debug("Attribute not found.");
			return null;
		}

		try {
			return attribute.getValue();
		} catch (ZigBeeClusterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public <T extends Cluster> Attribute openAttribute(String zigbeeAddress, Class<T> clusterId, AttributeDescriptor attributeId, ZigBeeEventListener listener) {
		final Device device = getDeviceByIndexOrEndpointId(zigbeeApi, zigbeeAddress);
		Cluster cluster = device.getCluster(clusterId);
		if (cluster == null) {
			return null;
		}
		Attribute attribute = cluster.getAttribute(attributeId.getId());
		if (attribute == null) {
			return null;
		}

		if (listener != null) {
			attribute.getReporter().addReportListener((ReportListener)listener, false);
		}
		return attribute;
	}

	public void closeAttribute(Attribute attribute, ZigBeeEventListener listener) {
		if (attribute != null && listener != null) {
			attribute.getReporter().removeReportListener((ReportListener)listener, false);
		}
	}

	public <T extends Cluster> T openCluster(String zigbeeAddress, Class<T> clusterId) {
		final Device device = getDeviceByIndexOrEndpointId(zigbeeApi, zigbeeAddress);
		return device.getCluster(clusterId);
	}

	public void closeCluster(Cluster cluster) {

	}

	/**
	 * Returns a list of all known devices
	 * @return list of devices
	 */
	public List<Device> getDeviceList() {
		return zigbeeApi.getDevices();
	}

	public void startDeviceDiscovery() {
		final List<Device> devices = zigbeeApi.getDevices();
		for (int i = 0; i < devices.size(); i++) {
			final Device device = devices.get(i);
			logger.debug("ZigBee '{}' device at address {}",
					device.getDeviceType(), device.getEndpointId());
			addNewDevice(device);
		}

		// Allow devices to join for 180 seconds
		zigbeeApi.permitJoin(180);

//		ZigBeeDiscoveryManager discoveryManager = zigbeeApi.getZigBeeDiscoveryManager();
//		discoveryManager.
	}
	
	/**
	 * Adds a device listener to receive updates on device status
	 * @param listener
	 */
	public void addDeviceListener(DeviceListener listener) {
		zigbeeApi.addDeviceListener(listener);
	}

	/**
	 * Removes a device listener to receive updates on device status
	 * @param listener
	 */
	public void removeDeviceListener(DeviceListener listener) {
		zigbeeApi.removeDeviceListener(listener);
	}

	@Override
	public void handleCommand(ChannelUID channelUID, Command command) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deviceAdded(Device device) {
		logger.debug("Device ADDED: '{}' {} {}", device.getDeviceType(),
					device.getEndpointId(), device.getProfileId());
		
		addNewDevice(device);
	}

	@Override
	public void deviceUpdated(Device device) {
		logger.debug("Device UPDATED: '{}' {} {}", device.getDeviceType(),
					device.getEndpointId(), device.getProfileId());

		ZigBeeEventListener listener = eventListeners.get(device.getEndpointId());
		if (listener != null) {
			listener.onEndpointStateChange();
		}
	}

	@Override
	public void deviceRemoved(Device device) {
		logger.debug("Device REMOVED: '{}' {} {}", device.getDeviceType(),
					device.getEndpointId(), device.getProfileId());

		ZigBeeEventListener listener = eventListeners.get(device.getEndpointId());
		if (listener != null) {
			listener.closeDevice();
		}
	}
	
	/**
	 * Adds a new device to the network.
	 * This starts a thread to read information about the device so we can
	 * present this information to the user.
	 * @param device
	 */
	private void addNewDevice(Device device) {
		DiscoveryThread discover = new DiscoveryThread();
		discover.run(device);
	}

	private class DiscoveryThread extends Thread {
		public void run(Device device) {
			logger.debug("Device Discovery: '{}' {} {}", device.getDeviceType(),
					device.getEndpointId(), device.getProfileId());
			
			String description = null;
			Object manufacturer = readAttribute(device, 0, 4);		// Manufacturer
			if(manufacturer != null) {
				description = manufacturer.toString();
				Object model = readAttribute(device, 0, 5);			// Model
				if(model != null) {
					description = manufacturer.toString().trim() + ":" + model.toString().trim();
				}
			}

			// Signal to the handlers that they are known...
			ZigBeeEventListener listener = eventListeners.get(device.getEndpointId());
			if (listener != null) {
				if (listener.openDevice()) {
					listener.onEndpointStateChange();
				}
			}

			discoveryService.deviceAdded(device, description);
		}
	}
}
