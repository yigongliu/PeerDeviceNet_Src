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

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.Utils;
import com.xconns.peerdevicenet.utils.Utils.IntfAddr;

public class WifiTransport implements Transport {
	public final static String TAG = "WifiTransport";

	RouterService routerService = null;
	Transport.Handler handler = null;
	final IntentFilter intentFilter = new IntentFilter();

	ConnectivityManager cm = null;
	WifiManager mWifiManager = null;

	boolean isWifiEnabled = false;
	DiscoveryMulticastThread mScanThread = null;
	DiscoveryMemberThread scannerGM = null;
	DiscoveryLeaderThread scannerGO = null;

	NetInfo netInfo = null;
	IntfAddr intfAddr = null;

	public WifiTransport(RouterService c) {
		routerService = c;
	}

	public void onCreate(Handler h) {
		handler = h;

		cm = (ConnectivityManager) routerService
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		mWifiManager = (WifiManager) routerService
				.getSystemService(Context.WIFI_SERVICE);
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION); // for
																			// connected
																			// or
																			// not
		intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION); // for
																		// enabled
																		// or
																		// not
	}

	public void onDestroy() {
		reset(true);
	}

	public void onPause() {
		// unregister recver for wifi conn change
		routerService.unregisterReceiver(myWifiRecver);
	}

	public void onResume() {
		// register recver for wifi conn change
		routerService.registerReceiver(myWifiRecver, intentFilter);
	}

	public boolean isEnabled() {
		return isWifiEnabled;
	}

	public boolean isNetworkConnected() {
		// TODO Auto-generated method stub
		return netInfo != null;
	}

	public int getType() {
		return NetInfo.WiFi;
	}

	public NetInfo getNetworkInfo() {
		return netInfo;
	}

	public IntfAddr getIntfAddr() {
		return intfAddr;
	}

	public void configureNetwork() {
		routerService.startActivity(new Intent(
				WifiManager.ACTION_PICK_WIFI_NETWORK));
	}

	public void createNetwork() {
		// meaningful only for wifi-direct
	}

	public void removeNetwork() {
		// meaningful only for wifi-direct
	}

	String getHotspotAddr(String addr) {
		return addr.substring(0, addr.lastIndexOf('.')) + ".1";
	}

	class ComboHandler implements Transport.SearchHandler {
		Transport.SearchHandler h = null;
		int count = 0;

		public ComboHandler(Transport.SearchHandler hd, int n) {
			h = hd;
			count = n;
		}

		public void onSearchStart(DeviceInfo grpLeader) {
			h.onSearchStart(grpLeader);
		}

		@Override
		public void onSearchFoundDevice(DeviceInfo device, boolean useSSL) {
			// TODO Auto-generated method stub
			h.onSearchFoundDevice(device, useSSL);
		}

		@Override
		public void onSearchComplete() {
			// TODO Auto-generated method stub
			synchronized (this) {
				count--;
				if (count > 0) {
					return;
				}
			}
			h.onSearchComplete();
		}

		@Override
		public void onError(String errInfo) {
			// TODO Auto-generated method stub
			h.onError(errInfo);
		}

	}

	public void startSearch(DeviceInfo myDevInfo, DeviceInfo grpLeader,
			int scanTimeout, Transport.SearchHandler h) {
		if (netInfo != null) {
			Log.d(TAG, "startSearch");

			if (grpLeader != null && grpLeader.addr != null) {
				if (grpLeader.addr.equals(myDevInfo.addr)) {
					Log.d(TAG, "start scannerGO");
					if (scannerGO != null) {
						scannerGO.stop_scan();
					}
					if (scannerGO == null) {
						Log.d(TAG, "scan timeout1=" + scanTimeout);
						scannerGO = new DiscoveryLeaderThread(routerService,
								netInfo, grpLeader.addr,
								scanTimeout,
								routerService.connTimeout);
						scannerGO.start();
					}
					scannerGO.start_scan(myDevInfo, scanTimeout, h);
				} else {
					// when we have group leader, just connect to group leader
					// for search peers
					Log.d(TAG, "start scannerGM");
					if (scannerGM != null) {
						scannerGM.closeFromInternal();
					}
					scannerGM = new DiscoveryMemberThread(routerService,
							grpLeader.addr, netInfo, myDevInfo, scanTimeout,
							routerService.connTimeout, h);
					scannerGM.start();
				}
			} else {
				// when no group leader, do both multicast and connect to
				// default group leader addr for search
				String hotspotAddr = getHotspotAddr(intfAddr.addr);
				Log.d(TAG, "hotspot addr=" + hotspotAddr);

				ComboHandler ch = new ComboHandler(h, 2);
				if (scannerGM != null) {
					scannerGM.closeFromInternal();
				}
				scannerGM = new DiscoveryMemberThread(routerService,
						hotspotAddr, netInfo, myDevInfo, scanTimeout,
						routerService.connTimeout, ch);
				scannerGM.start();

				if (mScanThread != null) {
					mScanThread.stop_scan();
				}
				mScanThread = new DiscoveryMulticastThread(routerService,
						mWifiManager, netInfo, myDevInfo, scanTimeout, ch);
				mScanThread.start();
			}
			h.onSearchStart(grpLeader);
			Log.d(TAG, "start wifi peer scanner");
		}
	}

	public void stopSearch() {
		if (netInfo != null) {
			Log.d(TAG, "stopSearch");
			if (scannerGO != null) {
				scannerGO.stop_scan();
			} else if (scannerGM != null) {
				scannerGM.closeFromInternal();
				scannerGM = null;
			}

			if (mScanThread != null) {
				mScanThread.stop_scan();
				mScanThread = null;
			}
		}
	}

	void reset(boolean fromGUI) {
		// stop scan
		if (scannerGO != null) {
			scannerGO.stop_scan();
			if (fromGUI) {
				scannerGO.close();
			} else {
				scannerGO.closeFromInternal();
			}
			scannerGO = null;
		}
		if (scannerGM != null) {
			if (fromGUI) {
				scannerGM.close();
			} else {
				scannerGM.closeFromInternal();
			}
			scannerGM = null;
		}
		if (mScanThread != null) {
			mScanThread.stop_scan();
			mScanThread = null;
		}

		NetInfo ni = netInfo;

		// reset data
		netInfo = null;
		intfAddr = null;

		if (ni != null) {
			handler.onNetworkDisconnected(ni);
		}
	}

	private BroadcastReceiver myWifiRecver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
				Log.d(TAG, "recv WIFI_STATE_CHANGED_ACTION");
				int wifiState = intent.getIntExtra(
						WifiManager.EXTRA_WIFI_STATE,
						WifiManager.WIFI_STATE_UNKNOWN);
				Log.d(TAG, "WIFI_STATE_CHANGED_ACTION, state=" + wifiState);
				if (WifiManager.WIFI_STATE_DISABLED == wifiState
						|| WifiManager.WIFI_STATE_DISABLING == wifiState) {
					Log.d(TAG, "wifi is disabling");
					isWifiEnabled = false;
				} else if (WifiManager.WIFI_STATE_ENABLED == wifiState
				/* || WifiManager.WIFI_STATE_ENABLING == wifiState */) {
					Log.d(TAG, "wifi is enabling");
					isWifiEnabled = true;
				}
				handler.onTransportEnabled(NetInfo.WiFi, isWifiEnabled);
				updateNetworkSettings(null);
			} else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
				Log.d(TAG, "recv CONNECTIVITY_ACTION");
				NetworkInfo ni = (NetworkInfo) intent
						.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
				Log.d(TAG, "recv CONNECTIVITY_ACTION: " + ni.toString());
				if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
					updateNetworkSettings(ni);
				}
			}
		}

	};

	void updateNetworkSettings(NetworkInfo ni0) {
		NetworkInfo wifiInfo = ni0;
		if (wifiInfo == null) {
			wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		}

		// check if connected
		if (!wifiInfo.isAvailable() || wifiInfo.getState() == NetworkInfo.State.DISCONNECTED
				|| wifiInfo.getState() == NetworkInfo.State.DISCONNECTING/*!wifiInfo.isConnectedOrConnecting()*/) {
			Log.d(TAG, "WIFI not connected");
			if (netInfo != null) {
				reset(false);
				// handler.onNetworkDisconnected(netInfo); already called inside
				// reset()
			}
			return;
		}

		// enabled & connected, get netinfo and addr
		Log.d(TAG, "WIFI connected");

		String ssid = null;
		String bssid = null;
		String ipaddr = null;
		String wifiName = null;
		int encrypt = NetInfo.NoPass;
		String passwd = null;
		boolean hidden = false;

		WifiInfo winfo = mWifiManager.getConnectionInfo();
		if (winfo != null) {
			ssid = winfo.getSSID();
			bssid = winfo.getBSSID();
			wifiName = ssid;
			if (wifiName == null)
				wifiName = bssid;
			if (wifiName != null && wifiName.length() > 0) {
				int myIp = winfo.getIpAddress();
				if (myIp != 0)
					ipaddr = Utils.getIpString(myIp);
			}
			int nid = winfo.getNetworkId();
			Log.d(TAG, "networkId = " + nid);
			WifiConfiguration wc = null;
			List<WifiConfiguration> existingConfigs = mWifiManager
					.getConfiguredNetworks();
			if (existingConfigs != null)
				for (WifiConfiguration existingConfig : existingConfigs) {
					Log.d(TAG, "netId = " + existingConfig.networkId);
					if (existingConfig.networkId == nid) {
						wc = existingConfig;
						break;
					}
				}
			if (wc != null) {
				hidden = wc.hiddenSSID;
				Log.d(TAG, "PSK=" + wc.preSharedKey + ", wepKey0="
						+ wc.wepKeys[0]);
				// if(wc.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN))
				// {
				if (wc.preSharedKey != null) {
					encrypt = NetInfo.WPA;
					passwd = wc.preSharedKey;
				}
				// else
				// if(wc.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.SHARED))
				// {
				else if (wc.wepKeys != null && wc.wepKeys[0] != null) {
					encrypt = NetInfo.WEP;
					passwd = wc.wepKeys[0];
				}
				if (wc.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)
						|| passwd == null) {
					encrypt = NetInfo.NoPass;
				}
				Log.d(TAG, "wifi ap ssid=" + ssid + ", pass=" + passwd);
			}
		}

		if (wifiName != null && ipaddr != null)
			Log.d(TAG, "wifi name=" + wifiName + ", addr=" + ipaddr);

		// update wifi name at gui
		if (wifiName != null && wifiName.length() > 0 && ipaddr != null
				&& ipaddr.length() > 0) {

			// if we have wifi net existing; check to see if switch to another
			// one
			// so disconnect it here first
			if (netInfo != null
					&& (!wifiName.equals(netInfo.name) || !ipaddr
							.equals(intfAddr.addr))) {
				reset(false);
				// handler.onNetworkDisconnected(netInfo); already called inside
				// reset()
			}

			/*
			 * if (netInfo == null || !netInfo.name.equals(wifiName) || intfAddr
			 * == null || !intfAddr.addr.equals(ipaddr)) {
			 */
			if (netInfo == null) {
				intfAddr = Utils.getIntfAddrByAddr(ipaddr);
				Log.d(TAG, "intfAddr="
						+ ((intfAddr == null) ? "null" : intfAddr.addr));

				if (intfAddr != null) {
					netInfo = new NetInfo(NetInfo.WiFi, wifiName, encrypt,
							passwd, hidden, null, intfAddr.intfName,
							intfAddr.addr, intfAddr.mcast);
					Log.d(TAG, "got netInfo: " + netInfo.toString());
					/*
					 * Matcher mat = pat.matcher(wifiName); if (mat.matches() &&
					 * netInfo.addr.startsWith(directPrefix)) { useDirectSearch
					 * = true; } else { useDirectSearch = false; } if
					 * (netInfo.addr.startsWith(hotspotPrefix)) {
					 * useHotspotSearch = true; } else { useHotspotSearch =
					 * false; }
					 */
					handler.onNetworkConnected(netInfo);
				}
			}
		}
	}
}
