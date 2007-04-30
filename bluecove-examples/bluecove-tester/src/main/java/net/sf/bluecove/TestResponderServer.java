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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.ServiceRegistrationException;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import junit.framework.Assert;

public class TestResponderServer implements CanShutdown, Runnable {
	
	public static int countSuccess = 0; 
	
	public static int countFailure = 0;
	
	public static TimeStatistic allServerDuration = new TimeStatistic(); 
	
	public static int countConnection = 0;
	
	public static int countRunningConnections = 0;
	
	public Thread thread;
	
	private long lastActivityTime;
	
	private boolean stoped = false;
	
	boolean isRunning = false;
	
	public static boolean discoverable = false;
	
	public static long discoverableStartTime = 0;
	
	private StreamConnectionNotifier serverConnection;
	
	private TestTimeOutMonitor monitor;
	
	private class ConnectionTread extends Thread {
		
		StreamConnection conn;
		
		boolean isRunning = true;
		
		ConnectionTread(StreamConnection conn) {
			super("ConnectionTread" + (++countConnection));
			this.conn = conn;
		}
		
		public void run() {
			InputStream is = null;
			OutputStream os = null;
			int testType = 0;
			countRunningConnections ++;
			try {
				is = conn.openInputStream();
				testType = is.read();

				if (testType == Consts.TEST_SERVER_TERMINATE) {
					Logger.info("Stop requested");
					shutdown();
					return;
				}
				Logger.debug("run test# " + testType);
				os = conn.openOutputStream();
				CommunicationTester.runTest(testType, true, is, os);
				Logger.debug("reply OK");
				os.write(Consts.SEND_TEST_REPLY_OK);
				os.write(testType);
				os.flush();
				countSuccess++;
				Logger.debug("Test# " + testType + " ok");
				try {
					Thread.sleep(Consts.serverSendCloseSleep);
				} catch (InterruptedException e) {
				}
			} catch (Throwable e) {
				countFailure++;
				Logger.error("Test# " + testType + " error", e);
			} finally {
				IOUtils.closeQuietly(is);
				IOUtils.closeQuietly(os);
				IOUtils.closeQuietly(conn);
				isRunning = false;
				countRunningConnections --;
				synchronized (this) {
					notifyAll();
				}
			}
			Logger.info("*Test Success:" + countSuccess + " Failure:" + countFailure);
		}
		
	}
	
	public TestResponderServer() throws BluetoothStateException {
		
		LocalDevice localDevice = LocalDevice.getLocalDevice();
		Logger.info("address:" + localDevice.getBluetoothAddress());
		Logger.info("name:" + localDevice.getFriendlyName());
 	    
		if (!Configuration.windowsCE) {
			Assert.assertNotNull("BT Address", localDevice.getBluetoothAddress());
			Assert.assertNotNull("BT Name", localDevice.getFriendlyName());
		}
		
	}
	
	public void run() {
		stoped = false;
		isRunning = true;
		if (!Configuration.continuous) {
			lastActivityTime = System.currentTimeMillis();
			monitor = new TestTimeOutMonitor(this, Consts.serverTimeOutMin);
		}
		try {
			LocalDevice localDevice = LocalDevice.getLocalDevice();
			if ((localDevice.getDiscoverable() == DiscoveryAgent.NOT_DISCOVERABLE) || (Configuration.testServerForceDiscoverable)) {
				if (!setDiscoverable()) {
					return;
				}
			}
			
			serverConnection = (StreamConnectionNotifier) Connector
					.open("btspp://localhost:"
							+ CommunicationTester.uuid
							+ ";name="
							+ Consts.RESPONDER_SERVERNAME
							//;authenticate=false;encrypt=false
							+ ";authorize=false");

			Logger.info("ResponderServer started " + Logger.timeNowToString());
			if (Configuration.testServiceAttributes) {
				ServiceRecord record = LocalDevice.getLocalDevice().getRecord(serverConnection);
				if (record == null) {
					Logger.warn("Bluetooth ServiceRecord is null");
				} else {
					buildServiceRecord(record);
				}
			}
			
			while (!stoped) {
				Logger.info("Accepting connection");
				StreamConnection conn = serverConnection.acceptAndOpen();
				if (!stoped) {
					Logger.info("Received connection");
					lastActivityTime = System.currentTimeMillis();
					ConnectionTread t = new ConnectionTread(conn);
					t.start();
					if (!Configuration.serverAcceptWhileConnected) {
						while (t.isRunning) {
							 synchronized (t) {
								 try {
									t.wait();
								} catch (InterruptedException e) {
									break;
								}
							 }
						}
					}
				} else {
					IOUtils.closeQuietly(conn);
				}
				Switcher.yield(this);
			}

			closeServer();
		} catch (Throwable e) {
			if (!stoped) {
				Logger.error("Server start error", e);
			}
		} finally {
			Logger.info("Server finished! " + Logger.timeNowToString());
			isRunning = false;
		}
		if (monitor != null) {
			monitor.finish();
		}
	}
	
	public static long avgServerDurationSec() {
		return allServerDuration.avgSec();
	}

	public boolean hasRunningConnections() {
		return (countRunningConnections > 0);
	}
	
	public long lastActivityTime() {
		return lastActivityTime;
		
	}
	
	public static void clear() {
		allServerDuration.clear();
	}
	
	private void closeServer() {
		if (serverConnection != null) {
			synchronized (this) {
				try {
					serverConnection.close();
					Logger.debug("serverConnection closed");
				} catch (Throwable e) {
					Logger.error("Server stop error", e);
				}
			}
			serverConnection = null;
		}
		setNotDiscoverable();
	}

	public static boolean setDiscoverable() {
		try {
			LocalDevice localDevice = LocalDevice.getLocalDevice();
			localDevice.setDiscoverable(DiscoveryAgent.GIAC);
			Logger.debug("Set Discoverable");
			discoverable = true;
			discoverableStartTime = System.currentTimeMillis();
			return true;
		} catch (Throwable e) {
			Logger.error("Start server error", e);
			return false;
		}
	}
	
	public static void setNotDiscoverable() {
		try {
			allServerDuration.add(Logger.since(discoverableStartTime));
			LocalDevice localDevice = LocalDevice.getLocalDevice();
			localDevice.setDiscoverable(DiscoveryAgent.NOT_DISCOVERABLE);
			Logger.debug("Set Not Discoverable");
			discoverable = false;
		} catch (Throwable e) {
			Logger.error("Stop server error", e);
		}
	}
	
	public void shutdown() {
		Logger.info("shutdownServer");
		stoped = true;
		thread.interrupt();
		closeServer();
	}
	
	public void updateServiceRecord() {
		if (serverConnection == null) {
			return;
		}
		try {
			ServiceRecord record = LocalDevice.getLocalDevice().getRecord(serverConnection);
			if (record != null) {
				updateVariableServiceRecord(record);
				LocalDevice.getLocalDevice().updateRecord(record);
			}
		} catch (Throwable e) {
			Logger.error("updateServiceRecord", e);
		}
	}
	
	private void updateVariableServiceRecord(ServiceRecord record) {
		byte[] data = new byte[16];
		
		data[0] = (byte)(Switcher.serverStartCount & 0xF);
		
		Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        data[1] = (byte)calendar.get(Calendar.MINUTE);
        
		record.setAttributeValue(Consts.VARIABLE_SERVICE_ATTRIBUTE_BYTES_ID,
		        new DataElement(DataElement.INT_16, data));
	}
	
    private void buildServiceRecord(ServiceRecord record) throws ServiceRegistrationException {
        String id = "";
    	try {
    		id = "pub";
			buildServiceRecordPub(record);
			id = "int";
			record.setAttributeValue(Consts.TEST_SERVICE_ATTRIBUTE_INT_ID,
			        new DataElement(Consts.TEST_SERVICE_ATTRIBUTE_INT_TYPE, Consts.TEST_SERVICE_ATTRIBUTE_INT_VALUE));
			id = "long";
			record.setAttributeValue(Consts.TEST_SERVICE_ATTRIBUTE_LONG_ID,
			        new DataElement(Consts.TEST_SERVICE_ATTRIBUTE_LONG_TYPE, Consts.TEST_SERVICE_ATTRIBUTE_LONG_VALUE));
			if (!Configuration.testIgnoreNotWorkingServiceAttributes) {
				id = "str";
				record.setAttributeValue(Consts.TEST_SERVICE_ATTRIBUTE_STR_ID, new DataElement(DataElement.STRING,
						Consts.TEST_SERVICE_ATTRIBUTE_STR_VALUE));
			}
			id = "url";
			record.setAttributeValue(Consts.TEST_SERVICE_ATTRIBUTE_URL_ID,
			        new DataElement(DataElement.URL, Consts.TEST_SERVICE_ATTRIBUTE_URL_VALUE));
			
			id = "bytes";
			record.setAttributeValue(Consts.TEST_SERVICE_ATTRIBUTE_BYTES_ID,
			        new DataElement(Consts.TEST_SERVICE_ATTRIBUTE_BYTES_TYPE, Consts.TEST_SERVICE_ATTRIBUTE_BYTES_VALUE));
			
			id = "variable";
			updateVariableServiceRecord(record);
			id = "update";
			//LocalDevice.getLocalDevice().updateRecord(record);
			
		} catch (Throwable e) {
			Logger.error("ServiceRecord " + id, e);
		}
    }
    
    public void setAttributeValue(ServiceRecord record, int attrID, DataElement attrValue) {
        try {
            if (!record.setAttributeValue(attrID, attrValue)) {
                Logger.error("SrvReg attrID=" + attrID);
            }
        } catch (Exception e) {
            Logger.error("SrvReg attrID=" + attrID, e);
        }
    }
    
    public void buildServiceRecordPub(ServiceRecord record) throws ServiceRegistrationException {
        final short UUID_PUBLICBROWSE_GROUP = 0x1002;
        final short ATTR_BROWSE_GRP_LIST = 0x0005;
        // Add the service to the 'Public Browse Group'
        DataElement browseClassIDList = new DataElement(DataElement.DATSEQ);
        UUID browseClassUUID = new UUID(UUID_PUBLICBROWSE_GROUP);
        browseClassIDList.addElement(new DataElement(DataElement.UUID, browseClassUUID));
        setAttributeValue(record, ATTR_BROWSE_GRP_LIST, browseClassIDList);
    }
    
	public static void main(String[] args) {
		JavaSECommon.initOnce();
		try {
			(new TestResponderServer()).run();
			if (TestResponderServer.countFailure > 0) {
				System.exit(1);
			} else {
				System.exit(0);
			}
		} catch (Throwable e) {
			Logger.error("start error ", e);
			System.exit(1);
		}
	}


}
