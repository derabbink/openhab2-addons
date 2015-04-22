/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bubblecloud.zigbee.network.port.ZigBeePort;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
//import org.openhab.binding.zigbee.network.port.ZigBeeSerialPortImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeCoordinatorCC2530Handler} is responsible for handling
 * commands, which are sent to one of the channels.
 * 
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeCoordinatorCC2530Handler extends ZigBeeCoordinatorHandler implements ZigBeePort {
	private String portId;

	private Logger logger = LoggerFactory
			.getLogger(ZigBeeCoordinatorCC2530Handler.class);

	public ZigBeeCoordinatorCC2530Handler(Bridge coordinator) {
		super(coordinator);
	}

	@Override
	public void handleCommand(ChannelUID channelUID, Command command) {
		// Note required!
	}

	@Override
	public void initialize() {
		logger.debug("Initializing ZigBee CC2530EMK serial bridge handler.");

		portId = (String) getConfig().get(PARAMETER_PORT);

		// Call the parent to finish any global initialisation
		super.initialize();

		logger.debug(
				"ZigBee Coordinator CC2530 opening Port:'{}' PAN:{}, Channel:{}",
				portId, Integer.toHexString(panId),
				Integer.toString(channelId));
		
		// TODO: Some of this needs to move to the parent class
		// TODO: Only the port initialisation should be done here and then pass
		// TODO: This to the parent to handle the protocol.
		// TODO: Needs splitting IO in the library!
        //discoveryModes.remove(DiscoveryMode.LinkQuality);
//        ZigBeePort serialPort = new ZigBeeSerialPortImpl(portId, 115200);

		initializeZigBee(this);
	}

	@Override
	public void dispose() {
	}

	@Override
	protected void updateStatus(ThingStatus status, ThingStatusDetail detail, String desc) {
		super.updateStatus(status, detail, desc);
		for (Thing child : getThing().getThings()) {
			child.setStatusInfo(new ThingStatusInfo(status, detail, desc));
		}
	}

	// The serial port.
	private SerialPort serialPort;
	
	// The serial port input stream.
	private InputStream inputStream;
	
	// The serial port output stream.
	private OutputStream outputStream;

    @Override
    public boolean open() {
        try {
            openSerialPort(portId, 115200);
            return true;
        } catch (Exception e) {
            logger.error("Error...", e);
            return false;
        }
    }

	private void openSerialPort(final String serialPortName, int baudRate) {
		logger.info("Connecting to serial port {}", serialPortName);
		try {
			CommPortIdentifier portIdentifier = CommPortIdentifier
					.getPortIdentifier(serialPortName);
			CommPort commPort = portIdentifier.open(
					"org.openhab.binding.zigbee", 2000);
			serialPort = (SerialPort) commPort;
			serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			this.serialPort.enableReceiveThreshold(1);
			this.serialPort.enableReceiveTimeout(120000);

			// RXTX serial port library causes high CPU load
			// Start event listener, which will just sleep and slow down event
			// loop
			// serialPort.addEventListener(this.receiveThread);
			// serialPort.notifyOnDataAvailable(true);

			logger.info("Serial port is initialized");
		} catch (NoSuchPortException e) {
			logger.error("Serial Error: Port {} does not exist", serialPortName);
			return;
		} catch (PortInUseException e) {
			logger.error("Serial Error: Port {} in use.", serialPortName);
			return;
		} catch (UnsupportedCommOperationException e) {
			logger.error(
					"Serial Error: Unsupported comm operation on Port {}.",
					serialPortName);
			return;
		}

		try {
			inputStream = serialPort.getInputStream();
			outputStream = serialPort.getOutputStream();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return;
	}

	@Override
	public void close() {
		try {
			if (serialPort != null) {
				while (inputStream.available() > 0) {
					try {
						Thread.sleep(100);
					} catch (final InterruptedException e) {
						logger.warn("Interrupted while waiting input stream to flush.");
					}
				}
				inputStream.close();
				outputStream.flush();
				outputStream.close();
				serialPort.close();
				// logger.info("Serial port '" + serialPort.getName() +
				// "' closed.");
				serialPort = null;
				inputStream = null;
				outputStream = null;
			}
		} catch (Exception e) {
			// logger.warn("Error closing serial port: '" + serialPort.getName()
			// + "'", e);
		}
	}

	@Override
	public OutputStream getOutputStream() {
		return outputStream;
	}

	@Override
	public InputStream getInputStream() {
		return inputStream;
	}

}
