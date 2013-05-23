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

import java.util.ArrayList;
import java.util.Timer;

import android.content.pm.PackageManager;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.core.Transport.SearchHandler;

public class TransportManager {

	public static final String TAG = "TransportManager";

	Transport actLink = null;
	Transport[] links = new Transport[NetInfo.NET_TYPES];
	{
		for (int i = 0; i < NetInfo.NET_TYPES; i++)
			links[i] = null;
	}

	RouterService context = null;
	
	Timer checkTimer = null;

	public TransportManager(RouterService c, Transport.Handler h) {
		context = c;

		if (isSupported(NetInfo.WiFiDirect)) {
			links[NetInfo.WiFiDirect] = new WifiDirectTransport(c);
			links[NetInfo.WiFiDirect].onCreate(h);
		}
		if (isSupported(NetInfo.WiFiHotspot)) {
			links[NetInfo.WiFiHotspot] = new WifiHotspotTransport(c);
			links[NetInfo.WiFiHotspot].onCreate(h);
		}
		if (isSupported(NetInfo.WiFi)) {
			links[NetInfo.WiFi] = new WifiTransport(c);
			links[NetInfo.WiFi].onCreate(h);
		}
		// bluetooth
		// mobile
	}

	public void onDestroy() {
		// do not destroy wifi direct group, because:
		// 1. it is either created outside of PeerDeviceNet, keep it
		// 2. it is a group created explicitly by user, let user kill it thru
		// PeerDeviceNet Gui
		// shutdown scanners
		for (int i = 0; i < NetInfo.NET_TYPES; i++)
			if (links[i] != null)
				links[i].onDestroy();
	}

	public void onResume() {
		for (int i = 0; i < NetInfo.NET_TYPES; i++)
			if (links[i] != null)
				links[i].onResume();
	}

	public void onPause() {
		for (int i = 0; i < NetInfo.NET_TYPES; i++)
			if (links[i] != null)
				links[i].onPause();
	}

	/*
	 * diff between transport and network: 1. transport can be supported and
	 * enabled with no network provisioned 2. all transports are active all the
	 * time, monitoring network connected/disconnected 3. for current design,
	 * only one network can be active at any moment: ie. you can only search
	 * current active network and connect to devices on this net
	 */
	public NetInfo setActiveNetwork(int netType) {
		if (netType == NetInfo.NoNet) {
			actLink = null;
			return null;
		}
		actLink = links[netType];
		if (actLink == null)
			return null;
		return  actLink.getNetworkInfo();
	}
	
	public NetInfo getNetwork(int netType) {
		return links[netType]!=null?links[netType].getNetworkInfo():null;
	}

	public NetInfo[] getAllNetworks() {
		ArrayList<NetInfo> nets = new ArrayList<NetInfo>();
		for (int i = 0; i < NetInfo.NET_TYPES; i++) {
			Transport link = links[i];
			if (link != null && link.getNetworkInfo() != null) {
				nets.add(link.getNetworkInfo());
			}
		}
		//
		return nets.toArray(new NetInfo[0]);
	}

	public NetInfo getActiveNetwork() {
		if (actLink != null)
			return actLink.getNetworkInfo();
		return null;
	}
	
	

	public int getNumNetworks() {
		int num = 0;
		for (int i = 0; i < NetInfo.NET_TYPES; i++) {
			Transport link = links[i];
			if (link != null && link.getNetworkInfo() != null) {
				num++;
			}
		}
		return num;
	}
	
	public int getNumTransports() {
		int num = 0;
		for (int i = 0; i < NetInfo.NET_TYPES; i++) {
			Transport link = links[i];
			if (link != null) {
				num++;
			}
		}
		return num;
	}

	public Transport setActiveTransport(int netType) {
		return actLink = links[netType];
	}

	public Transport getActiveTransport() {
		return actLink;
	}

	public Transport[] getAllTransports() {
		ArrayList<Transport> trans = new ArrayList<Transport>();
		for (int i = 0; i < NetInfo.NET_TYPES; i++) {
			Transport link = links[i];
			if (link != null) {
				trans.add(link);
			}
		}
		return trans.toArray(new Transport[0]);
	}
	
	public boolean isEnabled(int netType) {
		Transport t = links[netType];
		return t != null && t.isEnabled();
	}

	public boolean isSupported(int netType) {
		switch(netType) {
		case NetInfo.WiFi:
			return context.getPackageManager().hasSystemFeature(
					PackageManager.FEATURE_WIFI);
			
		case NetInfo.WiFiHotspot:
			return context.getPackageManager().hasSystemFeature(
					PackageManager.FEATURE_WIFI);
			
		case NetInfo.WiFiDirect:
			return context.getPackageManager().hasSystemFeature(
					PackageManager.FEATURE_WIFI_DIRECT);
		case NetInfo.Bluetooth:
			break;
		case NetInfo.Mobile:
			break;
		default:
			break;
		}
		return false;
	}

	// use android standard GUI to config
	void configureNetwork() {
		if (actLink != null) {
			actLink.configureNetwork();
		}
	}

	// the next 2 useful for wifi direct, empty for others
	void createNetwork() {
		if (actLink != null) {
			actLink.createNetwork();
		}
	}

	void removeNetwork() {
		if (actLink != null) {
			actLink.removeNetwork();
		}
	}

	// scan for peers
	void startSearch(DeviceInfo myInfo, DeviceInfo grpLeader, int searchTimeout, SearchHandler h) {
		if (actLink != null) {
			actLink.startSearch(myInfo, grpLeader, searchTimeout, h);
		}
	}

	void stopSearch() {
		if (actLink != null) {
			actLink.stopSearch();
		}
	}

}
