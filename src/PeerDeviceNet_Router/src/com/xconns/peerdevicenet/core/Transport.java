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

package com.xconns.peerdevicenet.core;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.Utils.IntfAddr;

//note: this is a local interface, no need to be pure async
//so here callback is pure for events
public interface Transport {
	interface Handler {
		void onError(int netType, String errInfo);
		
		//the following 3 methods are called from main GUI thread
		void onTransportEnabled(int netType, boolean enabled);

		//void onGroupCreated(NetInfo net);
		void onNetworkConnected(NetInfo net);

		//void onGroupRemoved();
		void onNetworkDisconnected(NetInfo net);
	}
	
	interface SearchHandler {
		//these methods are called from scan thread
		//peer search 
		void onSearchStart(DeviceInfo grpLeader);
		void onSearchFoundDevice(DeviceInfo device, boolean useSSL);
		void onSearchComplete();
		//
		void onError(String errInfo);
	};
		
	//life cycle
	void onCreate(Handler h);
	void onDestroy();
	void onPause();
	void onResume();
	
	//--- app interfaces ---
	//query
	boolean isEnabled();    //is transport enabled
	boolean isNetworkConnected();  //did we connect to a network
	int getType(); //return NetInfo.type
	NetInfo getNetworkInfo();  //get net info
	IntfAddr getIntfAddr(); //return my addr at this network
	
	//config
	void configureNetwork();    //use android standard GUI to config
	//the next 2 useful for wifi direct, empty for others
	void createNetwork();
	void removeNetwork();
	
	//scan for peers
	void startSearch(DeviceInfo myInfo, DeviceInfo grpLeader, int searchTimeout, SearchHandler h);
	void stopSearch();
	
}

