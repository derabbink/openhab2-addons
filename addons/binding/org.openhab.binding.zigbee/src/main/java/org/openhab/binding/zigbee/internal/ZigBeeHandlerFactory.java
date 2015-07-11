/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorCC2530Handler;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.handler.ZigBeeLightHandler;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeHandlerFactory} is responsible for creating things and thing
 * handlers.
 * 
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeHandlerFactory extends BaseThingHandlerFactory {
	private static final Logger logger = LoggerFactory
			.getLogger(ZigBeeHandlerFactory.class);

	@Override
	public boolean supportsThingType(ThingTypeUID thingTypeUID) {
		return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
	}

	@Override
	protected ThingHandler createHandler(Thing thing) {

		ThingTypeUID thingTypeUID = thing.getThingTypeUID();
		logger.debug("Creating handler for {}", thingTypeUID.toString());

		if (SUPPORTED_BRIDGE_TYPES_UIDS.contains(thingTypeUID)) {
			ZigBeeCoordinatorHandler handler = null;
			if (COORDINATOR_TYPE_CC2530.equals(thingTypeUID)) {
				handler = new ZigBeeCoordinatorCC2530Handler((Bridge) thing);
			}

			if (handler == null) {
				logger.error("ThingHandler not found for {}",
						thing.getThingTypeUID());
				return null;
			}

			// registerDeviceDiscoveryService(handler);
			return handler;
		} else if (SUPPORTED_LIGHT_DEVICE_TYPES_UIDS.contains(thingTypeUID)) {
			return new ZigBeeLightHandler(thing);
		} else {
			logger.debug("ThingHandler not found for {}",
					thing.getThingTypeUID());
			return null;
		}
	}

	@Override
	public Thing createThing(ThingTypeUID thingTypeUID,
			Configuration configuration, ThingUID thingUID, ThingUID bridgeUID) {

		if (SUPPORTED_BRIDGE_TYPES_UIDS.contains(thingTypeUID)) {
			ThingUID zigbeeBridgeUID = getBridgeThingUID(thingTypeUID,
					thingUID, configuration);
			return super.createThing(thingTypeUID, configuration,
					zigbeeBridgeUID, null);
		}
		if (SUPPORTED_DEVICE_TYPES_UIDS.contains(thingTypeUID)) {
			ThingUID deviceUID = getZigBeeDeviceUID(thingTypeUID, thingUID,
					configuration, bridgeUID);
			return super.createThing(thingTypeUID, configuration, deviceUID,
					bridgeUID);
		}
		throw new IllegalArgumentException("The thing type " + thingTypeUID
				+ " is not supported by the binding.");
	}

	private ThingUID getBridgeThingUID(ThingTypeUID thingTypeUID,
			ThingUID thingUID, Configuration configuration) {
		if (thingUID == null) {
			String SerialNumber = (String) configuration
					.get(ZigBeeBindingConstants.PARAMETER_PANID);
			thingUID = new ThingUID(thingTypeUID, SerialNumber);
		}
		return thingUID;
	}

	private ThingUID getZigBeeDeviceUID(ThingTypeUID thingTypeUID,
			ThingUID thingUID, Configuration configuration, ThingUID bridgeUID) {
		String SerialNumber = (String) configuration
				.get(ZigBeeBindingConstants.PARAMETER_MACADDRESS);

		if (thingUID == null) {
			thingUID = new ThingUID(thingTypeUID, SerialNumber,
					bridgeUID.getId());
		}
		return thingUID;
	}
}
