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

import android.os.Bundle;
import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;

interface IRouterConnectionHandler {
	oneway void onError(in String errInfo);
	
	//----------- network callbacks ---------
	//get all connected networks
	oneway void onGetNetworks(in NetInfo[] nets);
	oneway void onNetworkConnected(in NetInfo net);
	oneway void onNetworkDisconnected(in NetInfo net);
	//get current active network
	oneway void onGetActiveNetwork(in NetInfo net);
	oneway void onNetworkActivated(in NetInfo net);
	//search related
	oneway void onSearchStart(in DeviceInfo groupLeader);
	oneway void onSearchFoundDevice(in DeviceInfo device, boolean useSSL);
	oneway void onSearchComplete();
	
	//---------- connection callbacks -------
	oneway void onConnecting(in DeviceInfo device, in byte[] token);
	oneway void onConnectionFailed(in DeviceInfo device, int rejectCode);  
	oneway void onConnected(in DeviceInfo device);
	oneway void onDisconnected(in DeviceInfo device);
	oneway void onSetConnectionInfo();
	oneway void onGetConnectionInfo(String devName, boolean useSSL, int liveTime, int connTime, int searchTime);
	oneway void onGetDeviceInfo(in DeviceInfo device);
	oneway void onGetPeerDevices(in DeviceInfo[] devices);
}
