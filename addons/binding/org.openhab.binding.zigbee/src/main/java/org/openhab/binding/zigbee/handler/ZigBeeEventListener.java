/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import java.util.Dictionary;

import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;

public interface ZigBeeEventListener {
	public void onEndpointStateChange();

	public boolean openDevice();
	public void closeDevice();

	/**
	 * This method is called whenever the state of the given light has changed.
	 * The new state can be obtained by {@link FullLight#getState()}.
	 * 
	 * @param coordinator
	 *            The coordinator the changed light is connected to.
	 * @param light
	 *            The light which received the state update.
	 */
//	public void onLightStateChanged(ZigBeeCoordinatorHandler coordinator, ZigBeeLight light);

	/**
	 * This method us called whenever a light is removed.
	 * 
	 * @param coordinator
	 *            The coordinator the removed light was connected to.
	 * @param light
	 *            The light which is removed.
	 */
	// public void onLightRemoved(ZigBeeCoordinatorHandler coordinator,
	// ZigBeeLight light);

	/**
	 * This method us called whenever a light is added.
	 * 
	 * @param coordinator
	 *            The coordinator the added light was connected to.
	 * @param light
	 *            The light which is added.
	 */
//	public void onLightAdded(ZigBeeCoordinatorHandler coordinator, ZigBeeLight light);

}