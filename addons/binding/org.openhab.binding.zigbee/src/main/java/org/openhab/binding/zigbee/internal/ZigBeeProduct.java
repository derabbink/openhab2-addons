/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.internal;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

public class ZigBeeProduct {
    ThingTypeUID thingTypeUID;
    String manufacturer;
    String model;

    ZigBeeProduct(ThingTypeUID thingTypeUID, String manufacturer, String model) {
        this.thingTypeUID = thingTypeUID;
        this.manufacturer = manufacturer;
        this.model = model;
    }

    public boolean match(String manufacturer, String model) {
        if (this.manufacturer.equals(manufacturer) == false) {
            return false;
        }
        if (this.model.equals(model) == false) {
            return false;
        }
        return true;
    }

    public ThingTypeUID getThingTypeUID() {
        return thingTypeUID;
    }
}