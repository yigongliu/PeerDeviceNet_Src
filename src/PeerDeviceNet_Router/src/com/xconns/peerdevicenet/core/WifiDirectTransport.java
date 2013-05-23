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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.provider.Settings;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.Utils;
import com.xconns.peerdevicenet.utils.Utils.IntfAddr;

@SuppressLint("NewApi")
public class WifiDirectTransport implements Transport, ChannelListener {
	public final static String TAG = "WifiDirectTransport";

	// data used to setup wifi-direct net
	WifiP2pManager manager = null;
	Channel channel = null;
	BroadcastReceiver receiver = null;
	final IntentFilter intentFilter = new IntentFilter();
	// info about wifi-direct net
	WifiP2pInfo connInfo = null;
	WifiP2pGroup groupInfo = null;
	NetInfo netInfo = null;
	IntfAddr intfAddr = null;
	//
	boolean isWifiDirectEnabled = false;
	boolean retryChannel = false;
	//
	DiscoveryLeaderThread scannerGO = null;
	DiscoveryMemberThread scannerGM = null;
	//
	RouterService context = null;
	Transport.Handler handler = null;

	public WifiDirectTransport(RouterService c) {
		context = c;
	}

	public void onCreate(Transport.Handler h) {
		handler = h;

		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter
				.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter
				.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) context
				.getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(context, context.getMainLooper(), this);
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
	}

	public void onResume() {
		context.registerReceiver(receiver, intentFilter);
	}

	public void onPause() {
		context.unregisterReceiver(receiver);
	}

	public void onDestroy() {
		// do not destroy wifi direct group, because:
		// 1. it is either created outside of PeerDeviceNet, keep it
		// 2. it is a group created explicitly by user, let user kill it thru
		// PeerDeviceNet Gui
		// shutdown scanners
		reset(true);
	}

	void reset(boolean fromGUI) {
		if (scannerGO != null) {
			if(fromGUI)
				scannerGO.close();
			else
				scannerGO.closeFromInternal();
			scannerGO = null;
		}
		if (scannerGM != null) {
			if(fromGUI)
				scannerGM.close();
			else
				scannerGM.closeFromInternal();
		}

		NetInfo ni = netInfo;

		connInfo = null;
		groupInfo = null;
		netInfo = null;
		intfAddr = null;

		if (ni != null) {
			handler.onNetworkDisconnected(ni);
		}
	}

	// ----------------------

	public boolean isEnabled() {
		return isWifiDirectEnabled;
	}

	public boolean isNetworkConnected() {
		return connInfo != null;
	}

	public int getType() {
		return NetInfo.WiFiDirect;
	}

	public NetInfo getNetworkInfo() {
		return netInfo;
	}

	public IntfAddr getIntfAddr() {
		return intfAddr;
	}

	public void startSearch(DeviceInfo myDeviceInfo, DeviceInfo grpLeader, int scanTO,
			Transport.SearchHandler h) {
		if (connInfo != null) {
			if (scannerGO != null) {
				scannerGO.start_scan(myDeviceInfo, scanTO, h);
			} else {
				if (scannerGM != null) {
					scannerGM.close();
				}
				scannerGM = new DiscoveryMemberThread(context,
						connInfo.groupOwnerAddress.getHostAddress(), netInfo,
						myDeviceInfo, scanTO, context.connTimeout, h);
				scannerGM.start();
				Log.d(TAG, "start group memeber scanner");
			}
			h.onSearchStart(grpLeader);
		}
	}

	public void stopSearch() {
		if (connInfo != null) {
			if (scannerGO != null) {
				scannerGO.stop_scan();
			} else if (scannerGM != null){
				scannerGM.close();
				scannerGM = null;
			}
		}
	}

	public void configureNetwork() {
		context.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
		// ctxt.startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
	}

	public void createNetwork() {
		manager.createGroup(channel, new ActionListener() {

			@Override
			public void onFailure(int reason) {
				// TODO Auto-generated method stub
				handler.onError(NetInfo.WiFiDirect,
						"failed to create group, error code: " + reason);
				Log.d(TAG, "failed to create group, error code: " + reason);
			}

			@Override
			public void onSuccess() {
				// TODO Auto-generated method stub
			}

		});
	}

	public void removeNetwork() {
		manager.removeGroup(channel, new ActionListener() {

			@Override
			public void onFailure(int reason) {
				// TODO Auto-generated method stub
				handler.onError(NetInfo.WiFiDirect,
						"failed to remove group, error code: " + reason);
				Log.d(TAG, "failed to remove group, error code: " + reason);
			}

			@Override
			public void onSuccess() {
				// TODO Auto-generated method stub

			}

		});
	}

	// called by WifiDirect broadcaster
	void onEnabledStatus(boolean status) {
		isWifiDirectEnabled = status;
		handler.onTransportEnabled(NetInfo.WiFiDirect, status);
	}

	// ---- the following are standard wifi-direct callbacks ----

	@Override
	public void onChannelDisconnected() {
		// TODO Auto-generated method stub
		// we will try once more
		if (manager != null && !retryChannel) {
			Log.d(TAG, "Channel lost. Trying again");
			handler.onError(NetInfo.WiFiDirect, "Channel lost. Trying again");
			reset(false);
			retryChannel = true;
			channel = manager
					.initialize(context, context.getMainLooper(), this);
		} else {
			Log.d(TAG,
					"Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.");
		}
	}

	ConnectionInfoListener connInfoHandler = new ConnectionInfoListener() {

		@Override
		public void onConnectionInfoAvailable(WifiP2pInfo info) {
			// TODO Auto-generated method stub
			if (!info.groupFormed) {
				reset(false);
				Log.d(TAG, "group disbanded");
				return;
			}

			Log.d(TAG, "group exist");

			connInfo = info;

			// request group info
			manager.requestGroupInfo(channel, grpInfoHandler);

		}
	};

	GroupInfoListener grpInfoHandler = new GroupInfoListener() {

		@Override
		public void onGroupInfoAvailable(WifiP2pGroup group) {
			if (groupInfo != null) {
				String curSsid = groupInfo.getNetworkName();
				if (curSsid != null && curSsid.equals(group.getNetworkName())) {
					Log.d(TAG, "onGroupInfoAvailable: a new peer comes in existing group");
					return;
				}
			}
			
			groupInfo = group;
			String ownerAddr = connInfo.groupOwnerAddress
					.getHostAddress();

			if (group.isGroupOwner()) {
				intfAddr = Utils.getIntfAddrByAddr(ownerAddr);
			} else {
				if (ownerAddr != null) {
					int pos = ownerAddr.lastIndexOf('.');
					if (pos > 0) {
						String dirNetPrefix = ownerAddr.substring(0, pos+1);
						intfAddr = Utils.getFirstIntfAddrWithPrefix(dirNetPrefix);
						if (intfAddr != null) {
							Log.d(TAG, "find GM intfAddr by ownerAddr: "+intfAddr.toString());
						}
					}
				}
				
				if(intfAddr == null) {
					intfAddr = Utils.getIntfAddrByType(NetInfo.WiFiDirect);
					if (intfAddr != null) {
						Log.d(TAG, "find GM intfAddr by type: "+intfAddr.toString());
					}
				}

				if (intfAddr == null) {
					intfAddr = Utils.getFirstWifiDirectIntfAddr();
					if (intfAddr != null) {
						Log.d(TAG, "find GM intfAddr: "+intfAddr.toString());
					}
				}
				
				if (intfAddr == null) {
					Log.d(TAG,
							"failed to get intf addr info for: "
									+ group.toString());
					return;
				}
			}

			if (connInfo.groupFormed && connInfo.isGroupOwner) {
				Log.d(TAG, "start group owner scanner");
				if (scannerGO != null) {
					scannerGO.closeFromInternal();
				}
				scannerGO = new DiscoveryLeaderThread(context, netInfo,
						intfAddr.addr, context.searchTimeout, context.connTimeout);
				scannerGO.start();
			} else if (connInfo.groupFormed) {

			}

			// SSID is now known.
			String ssid = group.getNetworkName();
			Log.d(TAG, "ssid=" + ssid);
			if (ssid == null) ssid = "";
			String pass = group.getPassphrase();
			Log.d(TAG, "pass=" + pass);
			if (pass == null)
				pass = "";
			Log.d(TAG, "addr=" + intfAddr.addr);
			byte[] info = null;
			// as a flag for group owner, only send grp owner ip for GO device
			if (connInfo.isGroupOwner) {
				info = connInfo.groupOwnerAddress.getHostAddress().getBytes();
			}
			netInfo = new NetInfo(NetInfo.WiFiDirect, ssid, NetInfo.WPA, pass, false, info,
					intfAddr.intfName, intfAddr.addr, true);
			handler.onNetworkConnected(netInfo);
		}

	};
}
