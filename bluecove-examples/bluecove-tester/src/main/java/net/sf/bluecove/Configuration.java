/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2006-2007 Vlad Skarzhevskyy
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  @version $Id$
 */
package net.sf.bluecove;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import net.sf.bluecove.util.BooleanVar;
import net.sf.bluecove.util.CLDCStub;
import net.sf.bluecove.util.IOUtils;
import net.sf.bluecove.util.IntVar;
import net.sf.bluecove.util.J2MEStringTokenizer;
import net.sf.bluecove.util.Storage;

/**
 * 
 * This define different client and server work patterns to identify problem in
 * native code.
 * 
 * 
 * @author vlads
 * 
 */
public class Configuration {

	public static boolean useShortUUID = false;

	public static BooleanVar deviceClassFilter = new BooleanVar(true);

	public static BooleanVar discoverDevicesComputers = new BooleanVar(true);

	public static BooleanVar discoverDevicesPhones = new BooleanVar(true);

	public static boolean searchOnlyBluecoveUuid = true;

	public static boolean discoverySearchOnlyBluecoveUuid = false;

	/**
	 * Limit connections to precompiled list of test devices.
	 */
	public static BooleanVar listedDevicesOnly = new BooleanVar(false);

	/**
	 * This may hung forever on some Nokia devices.
	 */
	public static BooleanVar discoveryGetDeviceFriendlyName = new BooleanVar(false);

	public static UUID discoveryUUID = new UUID(0x0100); // L2CAP

	// public static UUID discoveryUUID = Consts.uuidL2CAPLong;

	public static Hashtable testDeviceNames = null;

	public static Hashtable ignoreDevices = null;

	public static Hashtable useDevices = null;

	public static boolean serverAcceptWhileConnected = false;

	public static boolean serverAcceptWhileConnectedOnJavaSE = true;

	public static boolean serverContinuous = true;

	public static BooleanVar clientContinuous = new BooleanVar(true);

	public static boolean clientContinuousDiscovery = true;

	public static boolean clientContinuousDiscoveryDevices = true;

	public static boolean clientContinuousServicesSearch = true;

	public static boolean clientTestConnections = true;

	// This test concurrent connections if you have Multiple servers running.
	public static boolean clientTestConnectionsMultipleThreads = true;

	public static BooleanVar authenticate = new BooleanVar(false);

	public static BooleanVar encrypt = new BooleanVar(false);

	public static boolean authorize = false;

	public static BooleanVar testRFCOMM = new BooleanVar(true);

	public static IntVar TEST_CASE_FIRST = new IntVar(1);

	public static IntVar TEST_CASE_LAST = new IntVar(Consts.TEST_LAST_WORKING);

	public static IntVar STERSS_TEST_CASE = new IntVar(Consts.TEST_BYTE);

	public static BooleanVar testL2CAP = new BooleanVar(true);

	public static IntVar TEST_CASE_L2CAP_FIRST = new IntVar(1);

	public static IntVar TEST_CASE_L2CAP_LAST = new IntVar(Consts.TEST_L2CAP_LAST_WORKING);

	public static boolean testServerOBEX_TCP = false;

	public static IntVar authenticateOBEX = new IntVar(0);

	public static BooleanVar testServiceAttributes = new BooleanVar(true);

	public static BooleanVar testAllServiceAttributes = new BooleanVar(false);

	/**
	 * Apparently Motorola Service Attribute STRING is not working. INT_4 not
	 * working on some Nokia and breakers its discovery by Motorola. INT_16 are
	 * truncated in discovery by WIDCOMM Service attributes are not supported on
	 * BlueSoleil
	 */
	public static BooleanVar testIgnoreNotWorkingServiceAttributes = new BooleanVar(true);

	public static BooleanVar testServerForceDiscoverable = new BooleanVar(false);

	public static boolean initializeLocalDevice = true;

	public static int clientSleepBetweenConnections = 4100;

	public static int serverSleepB4ClosingConnection = 1000;

	public static int clientTestTimeOutSec = 60;

	public static int serverTestTimeOutSec = 60;

	public static int serverMAXTimeSec = 80;

	public static int clientSleepOnConnectionRetry = 500;

	public static int clientSleepOnDeviceInquiryError = 10000;

	public static Storage storage;

	private static String lastServerURL = null;

	public static CLDCStub cldcStub;

	/**
	 * We can't add Motorola TCKAgent to this MIDlet.
	 */
	public static final boolean likedTCKAgent = true;

	/**
	 * Apparently on Motorola iDEN serverConnection.acceptAndOpen() never
	 * returns.
	 */
	public static boolean canCloseServer = true;

	public static boolean isJ2ME = false;

	public static boolean isBlueCove = false;

	public static boolean windowsXP = false;

	public static boolean windowsCE = false;

	public static boolean linux = false;

	public static boolean macOSx = false;

	public static boolean stackWIDCOMM = false;

	public static boolean supportL2CAP = true;

	public static boolean CLDC_1_0 = false;

	public static boolean logTimeStamp = false;

	public static boolean screenSizeSmall = false;

	static {
		testDeviceNames = new Hashtable();
		ignoreDevices = new Hashtable();
		useDevices = new Hashtable();
		loadNames(testDeviceNames, "bluecove.device.names.txt");
		loadNames(ignoreDevices, "bluecove.device.ignore.txt");
		loadNames(useDevices, "bluecove.device.use.txt");

		if ((ignoreDevices.size() != 0) || (useDevices.size() != 0)) {
			listedDevicesOnly.setValue(true);
		}

		String sysName = System.getProperty("os.name");
		if (sysName != null) {
			sysName = sysName.toLowerCase();
			if (sysName.indexOf("windows") != -1) {
				if (sysName.indexOf("ce") != -1) {
					windowsCE = true;
				} else {
					windowsXP = true;
				}
			} else if (sysName.indexOf("mac os x") != -1) {
				macOSx = true;
			} else if (sysName.indexOf("linux") != -1) {
				linux = true;
			}
		}
	}

	public static boolean isWhiteDevice(String bluetoothAddress) {
		String addr = bluetoothAddress.toUpperCase();
		if (useDevices.get(addr) != null) {
			return true;
		} else if (useDevices.size() > 0) {
			return false;
		} else if (ignoreDevices.get(addr) != null) {
			return false;
		}
		return (testDeviceNames.get(addr) != null);
	}

	private static void loadNames(Hashtable deviceNames, String resourceName) {
		InputStream inputstream = Configuration.class.getResourceAsStream("/" + resourceName);
		if (inputstream == null) {
			return;
		}
		StringBuffer b = new StringBuffer();
		try {
			byte[] buf = new byte[1024];
			int i = 0;
			while ((i = inputstream.read(buf)) != -1) {
				b.append(new String(buf, 0, i));
			}
		} catch (IOException e) {
			return;
		} finally {
			IOUtils.closeQuietly(inputstream);
		}
		J2MEStringTokenizer st = new J2MEStringTokenizer(b.toString(), "\n");
		while (st.hasMoreTokens()) {
			String s = st.nextToken().trim();
			if (s.startsWith("#") || s.length() == 0) {
				continue;
			}
			int idx = s.indexOf(',');
			if ((idx == -1) || (idx == s.length())) {
				deviceNames.put(s, s);
			} else {
				deviceNames.put(s.substring(0, idx).trim(), s.substring(idx + 1).trim());
			}
		}
	}

	public static UUID blueCoveUUID() {
		if (useShortUUID) {
			return Consts.uuidShort;
		} else {
			return Consts.uuidLong;
		}
	}

	public static UUID blueCoveL2CAPUUID() {
		if (useShortUUID) {
			return Consts.uuidL2CAPShort;
		} else {
			return Consts.uuidL2CAPLong;
		}
	}

	public static UUID blueCoveOBEXUUID() {
		return Consts.uuidOBEX;
	}

	public static int getRequiredSecurity() {
		int requiredSecurity = ServiceRecord.NOAUTHENTICATE_NOENCRYPT;
		if (Configuration.authenticate.booleanValue()) {
			if (Configuration.encrypt.booleanValue()) {
				requiredSecurity = ServiceRecord.AUTHENTICATE_ENCRYPT;
			} else {
				requiredSecurity = ServiceRecord.AUTHENTICATE_NOENCRYPT;
			}
		} else if (Configuration.encrypt.booleanValue()) {
			throw new IllegalArgumentException("Illegal encrypt configuration");
		}
		return requiredSecurity;
	}

	public static String serverURLParams() {
		StringBuffer buf = new StringBuffer();
		buf.append(";authenticate=").append(authenticate.booleanValue() ? "true" : "false");
		buf.append(";encrypt=").append(encrypt.booleanValue() ? "true" : "false");
		buf.append(";authorize=").append(authorize ? "true" : "false");
		return buf.toString();
	}

	public static String getLastServerURL() {
		if (lastServerURL == null) {
			lastServerURL = Configuration.storage.retriveData(Storage.configLastServiceURL);
		}
		return lastServerURL;
	}

	public static void setLastServerURL(String lastServerURL) {
		Configuration.lastServerURL = lastServerURL;
		if (Configuration.storage != null) {
			Configuration.storage.storeData(Storage.configLastServiceURL, lastServerURL);
		}
	}
}