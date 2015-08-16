package org.openhab.binding.zigbee.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZigBeeConfigProvider {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeConfigProvider.class);

    private static ThingTypeRegistry thingTypeRegistry;

    private static Set<ThingTypeUID> zigbeeThingTypeUIDList = new HashSet<ThingTypeUID>();
    private static List<ZigBeeProduct> productIndex = new ArrayList<ZigBeeProduct>();

    protected void setThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZigBeeConfigProvider.thingTypeRegistry = thingTypeRegistry;
    }

    protected void unsetThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZigBeeConfigProvider.thingTypeRegistry = null;
    }

    private static void initialiseZigBeeThings() {
        // Check that we know about the registry
        if (thingTypeRegistry == null) {
            return;
        }

        // Get all the thing types
        Collection<ThingType> thingTypes = thingTypeRegistry.getThingTypes();
        for (ThingType thingType : thingTypes) {
            // Is this for our binding?
            if (ZigBeeBindingConstants.BINDING_ID.equals(thingType.getBindingId()) == false) {
                continue;
            }

            // Create a list of all things supported by this binding
            zigbeeThingTypeUIDList.add(thingType.getUID());

            // Get the properties
            Map<String, String> thingProperties = thingType.getProperties();

            if (thingProperties.get("vendor") == null | thingProperties.get("model") == null) {
                continue;
            }

            productIndex.add(
                    new ZigBeeProduct(thingType.getUID(), thingProperties.get("vendor"), thingProperties.get("model")));
        }
    }

    public static List<ZigBeeProduct> getProductIndex() {
        if (productIndex.size() == 0) {
            initialiseZigBeeThings();
        }
        return productIndex;
    }

    public static Set<ThingTypeUID> getSupportedThingTypes() {
        if (zigbeeThingTypeUIDList.size() == 0) {
            initialiseZigBeeThings();
        }
        return zigbeeThingTypeUIDList;
    }

    public static ThingType getThingType(ThingTypeUID thingTypeUID) {
        // Check that we know about the registry
        if (thingTypeRegistry == null) {
            return null;
        }

        return thingTypeRegistry.getThingType(thingTypeUID);
    }
}