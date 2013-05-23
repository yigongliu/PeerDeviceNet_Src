/*
 * Copyright (C) 2013 Yigong Liu, XCONNS, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
