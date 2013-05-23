/*************************************************************************
 * 
 * XCONNS CONFIDENTIAL
 * __________________
 * 
 *  2012 - 2013  XCONNS, LLC 
 *  All Rights Reserved.
 * 
 * NOTICE:  All information contained herein is, and remains
 * the property of XCONNS, LLC and its suppliers, if any.  
 * The intellectual and technical concepts contained herein are 
 * proprietary to XCONNS, LLC and its suppliers and may be covered 
 * by U.S. and Foreign Patents, patents in process, and are protected 
 * by trade secret or copyright law. Dissemination of this information
 * or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from XCONNS, LLC.
 */

package com.xconns.peerdevicenet;

import com.xconns.peerdevicenet.IRouterConnectionHandler;
import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import android.os.Bundle;

interface IRouterConnectionService {
	//shutdown router service
	oneway void shutdown();
	
	//startSession return sessionId
	int startSession(in IRouterConnectionHandler h);
	oneway void stopSession(int sessionId);
	
	//------ network api ------
	//get current connected networks
	oneway void getNetworks(int sessionId);
	//get current active network
	oneway void getActiveNetwork(int sessionId);
	oneway void activateNetwork(int sessionId, in NetInfo net);
	
	//peer device discovery/search
	oneway void startPeerSearch(int sessionId, in DeviceInfo groupLeader, int timeout);
	oneway void stopPeerSearch(int sessionId);
	
	//------ connection api ------
	oneway void connect(int sessionId, in DeviceInfo peer, in byte[] token, int timeout);  
	oneway void disconnect(int sessionId, in DeviceInfo peer);
	oneway void acceptConnection(int sessionId, in DeviceInfo peer);
	oneway void denyConnection(int sessionId, in DeviceInfo peer, int rejectCode);
	
	//query api - get & set my connection settings
	oneway void setConnectionInfo(int sessionId, String devName, boolean useSSL, int liveTime, int connTime, int searchTime);
	oneway void getConnectionInfo(int sessionId);
	oneway void getDeviceInfo(int sessionId);
	//get peer devices in the network
	oneway void getPeerDevices(int sessionId);
	
}
