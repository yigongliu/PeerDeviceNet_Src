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
