/**
 * BlueCove BlueZ module - Java library for Bluetooth on Linux
 *  Copyright (C) 2008 Mina Shokry
 *  Copyright (C) 2007 Vlad Skarzhevskyy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @version $Id$
 */
package com.intel.bluetooth;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.ServiceRegistrationException;
import javax.bluetooth.UUID;

class BluetoothStackBlueZ implements BluetoothStack, DeviceInquiryRunnable, SearchServicesRunnable {

	static final int NATIVE_LIBRARY_VERSION = BlueCoveImpl.nativeLibraryVersionExpected;

	private int deviceID;

	private int deviceDescriptor;

	private Map/* <String,String> */propertiesMap;

	private DiscoveryListener discoveryListener;

	private Vector/* <RemoteDevice> */discoveredDevices;

	private boolean deviceInquiryCanceled = false;

	// Used mainly in Unit Tests
	static {
		NativeLibLoader.isAvailable(BlueCoveImpl.NATIVE_LIB_BLUEZ, BluetoothStackBlueZ.class);
	}

	BluetoothStackBlueZ() {
	}

	// --- Library initialization

	public String getStackID() {
		return BlueCoveImpl.STACK_BLUEZ;
	}

	public native int getLibraryVersionNative();

	public int getLibraryVersion() throws BluetoothStateException {
		int version = getLibraryVersionNative();
		if (version != NATIVE_LIBRARY_VERSION) {
			throw new BluetoothStateException("BlueCove native library version mismatch");
		}
		return version;
	}

	public int detectBluetoothStack() {
		return BlueCoveImpl.BLUECOVE_STACK_DETECT_BLUEZ;
	}

	private native int nativeGetDeviceID() throws BluetoothStateException;

	private native int nativeOpenDevice(int deviceID) throws BluetoothStateException;

	public void initialize() throws BluetoothStateException {
		deviceID = nativeGetDeviceID();
		deviceDescriptor = nativeOpenDevice(deviceID);
		propertiesMap = new TreeMap/* <String,String> */();
		propertiesMap.put("bluetooth.api.version", "1.1");
	}

	private native void nativeCloseDevice(int deviceDescriptor);

	public void destroy() {
		nativeCloseDevice(deviceDescriptor);
	}

	public native void enableNativeDebug(Class nativeDebugCallback, boolean on);

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#isCurrentThreadInterruptedCallback()
	 */
	public boolean isCurrentThreadInterruptedCallback() {
		return Thread.interrupted();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#getFeatureSet()
	 */
	public int getFeatureSet() {
		return FEATURE_SERVICE_ATTRIBUTES | FEATURE_L2CAP;
	}

	// --- LocalDevice

	private native long getLocalDeviceBluetoothAddressImpl(int deviceDescriptor) throws BluetoothStateException;

	public String getLocalDeviceBluetoothAddress() throws BluetoothStateException {
		return RemoteDeviceHelper.getBluetoothAddress(getLocalDeviceBluetoothAddressImpl(deviceDescriptor));
	}

	private native int nativeGetDeviceClass(int deviceDescriptor);

	public DeviceClass getLocalDeviceClass() {
		int record = nativeGetDeviceClass(deviceDescriptor);
		if (record == 0xff000000) {
			// could not be determined
			return null;
		}
		return new DeviceClass(record);
	}

	private native String nativeGetDeviceName(int deviceDescriptor);

	public String getLocalDeviceName() {
		return nativeGetDeviceName(deviceDescriptor);
	}

	public boolean isLocalDevicePowerOn() {
		// Have no idea how turn on and off device on BlueZ, as well to how to
		// detect this condition.
		return true;
	}

	public String getLocalDeviceProperty(String property) {
		return (String) propertiesMap.get(property);
	}

	private native int nativeGetLocalDeviceDiscoverable(int deviceDescriptor);

	public int getLocalDeviceDiscoverable() {
		return nativeGetLocalDeviceDiscoverable(deviceDescriptor);
	}

	private native int nativeSetLocalDeviceDiscoverable(int deviceDescriptor, int mode);

	public boolean setLocalDeviceDiscoverable(int mode) throws BluetoothStateException {
		int error = nativeSetLocalDeviceDiscoverable(deviceDescriptor, mode);
		if (error != 0) {
			throw new BluetoothStateException("Unable to change discovery mode. It may be because you aren't root");
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#setLocalDeviceServiceClasses(int)
	 */
	public void setLocalDeviceServiceClasses(int classOfDevice) {
		throw new NotSupportedRuntimeException(getStackID());
	}

	// --- Device Inquiry

	public boolean startInquiry(int accessCode, DiscoveryListener listener) throws BluetoothStateException {
		if (discoveryListener != null) {
			throw new BluetoothStateException("Another inquiry already running");
		}
		discoveryListener = listener;
		discoveredDevices = new Vector();
		deviceInquiryCanceled = false;
		return DeviceInquiryThread.startInquiry(this, accessCode, listener);
	}

	private native int runDeviceInquiryImpl(DeviceInquiryThread startedNotify, int deviceID, int deviceDescriptor,
			int accessCode, int inquiryLength, int maxResponses, DiscoveryListener listener)
			throws BluetoothStateException;

	public int runDeviceInquiry(DeviceInquiryThread startedNotify, int accessCode, DiscoveryListener listener)
			throws BluetoothStateException {
		try {
			int discType = runDeviceInquiryImpl(startedNotify, deviceID, deviceDescriptor, accessCode, 8, 20, listener);
			if (deviceInquiryCanceled) {
				return DiscoveryListener.INQUIRY_TERMINATED;
			}
			return discType;
		} finally {
			discoveryListener = null;
			discoveredDevices = null;
		}
	}

	public void deviceDiscoveredCallback(DiscoveryListener listener, long deviceAddr, int deviceClass,
			String deviceName, boolean paired) {
		RemoteDevice remoteDevice = RemoteDeviceHelper.createRemoteDevice(this, deviceAddr, deviceName, paired);
		if (deviceInquiryCanceled || (discoveryListener == null) || (discoveredDevices == null)
				|| (discoveredDevices.contains(remoteDevice))) {
			return;
		}
		discoveredDevices.addElement(remoteDevice);
		DeviceClass cod = new DeviceClass(deviceClass);
		DebugLog.debug("deviceDiscoveredCallback address", remoteDevice.getBluetoothAddress());
		DebugLog.debug("deviceDiscoveredCallback deviceClass", cod);
		listener.deviceDiscovered(remoteDevice, cod);
	}

	private native boolean deviceInquiryCancelImpl(int deviceDescriptor);

	public boolean cancelInquiry(DiscoveryListener listener) {
		if (discoveryListener != null && discoveryListener == listener) {
			deviceInquiryCanceled = true;
			return deviceInquiryCancelImpl(deviceDescriptor);
		}
		return false;
	}

	private native String getRemoteDeviceFriendlyNameImpl(int deviceDescriptor, long remoteAddress) throws IOException;

	public String getRemoteDeviceFriendlyName(long address) throws IOException {
		return getRemoteDeviceFriendlyNameImpl(deviceDescriptor, address);
	}

	// --- Service search

	private native long[] nativeSearchServices(UUID[] uuids, long remoteDeviceAddress) throws SearchServicesException;

	public int runSearchServices(SearchServicesThread startedNotify, int[] attrSet, UUID[] uuidSet,
			RemoteDevice device, DiscoveryListener listener) throws BluetoothStateException {
		startedNotify.searchServicesStartedCallback();
		long[] handles;
		try {
			handles = nativeSearchServices(uuidSet, RemoteDeviceHelper.getAddress(device));
		} catch (SearchServicesDeviceNotReachableException e) {
			return DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE;
		} catch (SearchServicesTerminatedException e) {
			return DiscoveryListener.SERVICE_SEARCH_TERMINATED;
		} catch (SearchServicesException e) {
			return DiscoveryListener.SERVICE_SEARCH_ERROR;
		}
		if (handles == null)
			return DiscoveryListener.SERVICE_SEARCH_ERROR;
		else if (handles.length > 0) {
			ServiceRecord[] records = new ServiceRecordImpl[handles.length];
			boolean hasError = false;
			for (int i = 0; i < handles.length; i++) {
				records[i] = new ServiceRecordImpl(this, device, handles[i]);
				try {
					records[i].populateRecord(new int[] { 0x0000, 0x0001, 0x0002, 0x0003, 0x0004 });
					if (attrSet != null)
						// crash here
						records[i].populateRecord(attrSet);
				} catch (Exception e) {
					System.out.println("\t\texception");
					e.printStackTrace();
					DebugLog.debug("populateRecord error", e);
					hasError = true;
				}
				if (startedNotify.isTerminated())
					return DiscoveryListener.SERVICE_SEARCH_TERMINATED;
			}
			listener.servicesDiscovered(startedNotify.getTransID(), records);
			if (hasError)
				return DiscoveryListener.SERVICE_SEARCH_ERROR;
			else
				return DiscoveryListener.SERVICE_SEARCH_COMPLETED;
		} else
			return DiscoveryListener.SERVICE_SEARCH_NO_RECORDS;
	}

	public int searchServices(int[] attrSet, UUID[] uuidSet, RemoteDevice device, DiscoveryListener listener)
			throws BluetoothStateException {
		return SearchServicesThread.startSearchServices(this, attrSet, uuidSet, device, listener);
	}

	public boolean cancelServiceSearch(int transID) {
		// I didn't find a way to stop service search yet.
		return false;
	}

	private native boolean nativePopulateServiceRecordAttributeValues(long remoteDeviceAddress, long handle,
			int[] attrIDs, ServiceRecordImpl serviceRecord);

	public boolean populateServicesRecordAttributeValues(ServiceRecordImpl serviceRecord, int[] attrIDs)
			throws IOException {
		long remoteDeviceAddress = RemoteDeviceHelper.getAddress(serviceRecord.getHostDevice());
		return nativePopulateServiceRecordAttributeValues(remoteDeviceAddress, serviceRecord.getHandle(), attrIDs,
				serviceRecord);
	}

	// --- Client RFCOMM connections

	public long connectionRfOpenClientConnection(BluetoothConnectionParams params) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public void connectionRfCloseClientConnection(long handle) throws IOException {
		// TODO Auto-generated method stub

	}

	public int getSecurityOpt(long handle, int expected) throws IOException {
		return expected;
	}

	// --- Server RFCOMM connections

	public long rfServerOpen(BluetoothConnectionNotifierParams params, ServiceRecordImpl serviceRecord)
			throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public void rfServerClose(long handle, ServiceRecordImpl serviceRecord) throws IOException {
		// TODO Auto-generated method stub

	}

	public void rfServerUpdateServiceRecord(long handle, ServiceRecordImpl serviceRecord, boolean acceptAndOpen)
			throws ServiceRegistrationException {
		// TODO Auto-generated method stub

	}

	public long rfServerAcceptAndOpenRfServerConnection(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public void connectionRfCloseServerConnection(long handle) throws IOException {
		// TODO Auto-generated method stub
	}

	// --- Shared Client and Server RFCOMM connections

	public void connectionRfFlush(long handle) throws IOException {
		// TODO Auto-generated method stub

	}

	public int connectionRfRead(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public int connectionRfRead(long handle, byte[] b, int off, int len) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public int connectionRfReadAvailable(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public void connectionRfWrite(long handle, int b) throws IOException {
		// TODO Auto-generated method stub

	}

	public void connectionRfWrite(long handle, byte[] b, int off, int len) throws IOException {
		// TODO Auto-generated method stub

	}

	public long getConnectionRfRemoteAddress(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	// --- Client and Server L2CAP connections

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2OpenClientConnection(com.intel.bluetooth.BluetoothConnectionParams,
	 *      int, int)
	 */
	public long l2OpenClientConnection(BluetoothConnectionParams params, int receiveMTU, int transmitMTU)
			throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2CloseClientConnection(long)
	 */
	public void l2CloseClientConnection(long handle) throws IOException {
		// TODO Auto-generated method stub
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerOpen(com.intel.bluetooth.BluetoothConnectionNotifierParams,
	 *      int, int, com.intel.bluetooth.ServiceRecordImpl)
	 */
	public long l2ServerOpen(BluetoothConnectionNotifierParams params, int receiveMTU, int transmitMTU,
			ServiceRecordImpl serviceRecord) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerUpdateServiceRecord(long,
	 *      com.intel.bluetooth.ServiceRecordImpl, boolean)
	 */
	public void l2ServerUpdateServiceRecord(long handle, ServiceRecordImpl serviceRecord, boolean acceptAndOpen)
			throws ServiceRegistrationException {
		// TODO Auto-generated method stub
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerAcceptAndOpenServerConnection(long)
	 */
	public long l2ServerAcceptAndOpenServerConnection(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2CloseServerConnection(long)
	 */
	public void l2CloseServerConnection(long handle) throws IOException {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerClose(long,
	 *      com.intel.bluetooth.ServiceRecordImpl)
	 */
	public void l2ServerClose(long handle, ServiceRecordImpl serviceRecord) throws IOException {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2Ready(long)
	 */
	public boolean l2Ready(long handle) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2receive(long, byte[])
	 */
	public int l2Receive(long handle, byte[] inBuf) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2send(long, byte[])
	 */
	public void l2Send(long handle, byte[] data) throws IOException {
		// TODO Auto-generated method stub
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2GetReceiveMTU(long)
	 */
	public int l2GetReceiveMTU(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2GetTransmitMTU(long)
	 */
	public int l2GetTransmitMTU(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2RemoteAddress(long)
	 */
	public long l2RemoteAddress(long handle) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}
}