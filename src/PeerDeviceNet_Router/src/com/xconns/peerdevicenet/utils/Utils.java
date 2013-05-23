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

package com.xconns.peerdevicenet.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;

@SuppressLint("NewApi")
public class Utils {
	// Debugging
	private static final String TAG = "Utils";
	
	public final static int ANDROID_VERSION = Integer.valueOf(android.os.Build.VERSION.SDK);

	public static final String P2P_INTF = "p2p";
	public static final String WLAN_INTF = "wlan";
	
	public static final String WifiDirectPrefix = "192.168.49.";
	public static final String WifiHotspotPrefix = "192.168.43.";
	
	public static class IntfAddr {
		public String intfName;  //interface name of this address in this net
		public String addr;      //local addr in this net
		public boolean mcast;    //does this interface/net support multicast?
		public IntfAddr(String i, String a, boolean m) {
			intfName = i;
			addr = a;
			mcast = m;
		}
		public String toString() {
			return String.format("intfName: %s, addr:%s, supportMcast: %b", intfName, addr, mcast);
		}
	}
	
	//get ip address based on interface name
	public static IntfAddr getIntfAddrByType(int netType) {
		String intfName = null;
		if (NetInfo.WiFi == netType) {
			intfName = WLAN_INTF;
		}
		else if (NetInfo.WiFiDirect == netType) {
			intfName = P2P_INTF;
		}
		else {
			return null;
		}
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				boolean mcast = false;
				if (ANDROID_VERSION > 8) {
					mcast = intf.supportsMulticast();
				}
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address) {
						String iface = intf.getName();
						if(iface.matches(".*" +intfName+ ".*")){
							if (intfName.equals(WLAN_INTF) && iface.matches(".*" +P2P_INTF+ ".*")) {
								//some p2p intf named : p2p-wlan
								continue;
							}
							return new IntfAddr(iface, inetAddress.getHostAddress(), mcast);
						}
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}

	public static IntfAddr getIntfAddrByAddr(String addr) {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				boolean mcast = false;
				if (ANDROID_VERSION > 8) {
					mcast = intf.supportsMulticast();
				}
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address) {
						if (inetAddress.getHostAddress().equals(addr)) {
							return new IntfAddr(intf.getName(), addr, mcast);
						}
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		} catch (NullPointerException en) {
			Log.e(TAG, "getIntfAddrByAddr", en);
		}
		return null;
	}
	

	// gets the ip address of your phone's network
	// this will return any ip addr including 3g, or wifi
	public static IntfAddr getFirstIntfAddr() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				boolean mcast = false;
				if (ANDROID_VERSION > 8) {
					mcast = intf.supportsMulticast();
				}
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address) {
						return new IntfAddr(intf.getName(), inetAddress.getHostAddress(), mcast);
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}
	
	/*
	 * type = 1 - class A private network: 10.x.x.x
	 * type = 2 - class B private network: 172.16.0.0 - 172.31.255.255
	 * type = 3 - class C private network: 192.168.x.x
	 */
	public static IntfAddr getFirstPrivateIntfAddr(int type) {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				boolean mcast = false;
				if (ANDROID_VERSION > 8) {
					mcast = intf.supportsMulticast();
				}
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					String addr = inetAddress.getHostAddress();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address &&
							addr != null) {
						switch(type) {
						case 1:
							if (addr.startsWith("10.")) {
								return new IntfAddr(intf.getName(), addr, mcast);
							}
							break;
						case 2:
							if (addr.compareTo("172.16.0.0") >= 0 &&
									addr.compareTo("172.31.255.255") <= 0) {
								return new IntfAddr(intf.getName(), addr, mcast);
							}
							break;
						case 3:
							if (addr.startsWith("192.168.")) {
								return new IntfAddr(intf.getName(), addr, mcast);
							}							
							break;
						}
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}
	
	public static IntfAddr getFirstIntfAddrWithPrefix(String prefix) {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				boolean mcast = false;
				if (ANDROID_VERSION > 8) {
					mcast = intf.supportsMulticast();
				}
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					String addr = inetAddress.getHostAddress();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address 
							&& addr != null && addr.startsWith(prefix)) {
						return new IntfAddr(intf.getName(), addr, mcast);
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}
	
	public static IntfAddr getFirstPrivateIntfAddrWithPrefix(String prefix) {
		IntfAddr privAddr = getFirstIntfAddrWithPrefix(prefix);
		if (privAddr != null) {
			return privAddr;
		}
		//other class C private
		privAddr = getFirstPrivateIntfAddr(3);
		if (privAddr != null) {
			return privAddr;
		}
		/*
		//class B
		privAddr = getFirstPrivateIntfAddr(2);
		if (privAddr != null) {
			return privAddr;
		}
		//class A private
		privAddr = getFirstPrivateIntfAddr(1);
		if (privAddr != null) {
			return privAddr;
		}
		*/
		return null;
	}

	public static IntfAddr getFirstWifiHotspotIntfAddr() {
		return getFirstPrivateIntfAddrWithPrefix(WifiHotspotPrefix);
	}

	public static IntfAddr getFirstWifiDirectIntfAddr() {
		return getFirstPrivateIntfAddrWithPrefix(WifiDirectPrefix);		
	}
	
	public static List<IntfAddr> getAllIntfAddr() {
		List<IntfAddr> addrs = new ArrayList<IntfAddr>();
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				boolean mcast = false;
				if (ANDROID_VERSION > 8) {
					mcast = intf.supportsMulticast();
				}
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address) {
						addrs.add(new IntfAddr(intf.getName(),
								inetAddress.getHostAddress(), mcast));
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return addrs;
	}
	
	public static String getWifiIpAddr(Context ctx) {
		//first check if wifi addrs available
		ConnectivityManager cm = (ConnectivityManager) ctx
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (wifiInfo != null && wifiInfo.isConnected()) {
			WifiManager mWifiManager = (WifiManager) ctx
					.getSystemService(Context.WIFI_SERVICE);
			WifiInfo winfo = mWifiManager.getConnectionInfo();
			if (winfo != null) {
				String ssid = winfo.getSSID();
				if (ssid != null && ssid.length() > 0) {
					int myIp = winfo.getIpAddress();
					if (myIp != 0)
						return Utils.getIpString(myIp);
				}
			}
		}
		return null;
	}
	
	public static String getLocalIpAddr(Context ctx) {
		//first check if wifi addrs available
		String wifiIp = getWifiIpAddr(ctx);
		if (wifiIp != null) return wifiIp;
		IntfAddr adr = getFirstIntfAddr();
		if (adr != null) return adr.addr;
		return null;
	}
	
	// gets the ip address of your phone's network
	// this will return any ip addr including 3g, or wifi
	public static String getLocalIpAddr2() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress instanceof Inet4Address) {
						return inetAddress.getHostAddress();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}
	
	public static String getIpString(int ip) {
		StringBuilder str = new StringBuilder();
		str.append(ip & 0XFF).append(".")
			.append(((ip >> 8) & 0xFF)).append(".")
			.append(((ip >> 16) & 0xFF)).append(".")
			.append(((ip >> 24) & 0xFF));
		Log.d(TAG, "got ip: " + str.toString());
		return str.toString();
	}
	

	public static Bundle net2Bundle(NetInfo net) {
		Bundle b = new Bundle();
		b.putInt(Router.NET_TYPE, net.type);
		b.putString(Router.NET_NAME, net.name);
		b.putString(Router.NET_PASS, net.pass);
		b.putByteArray(Router.NET_INFO, net.info);
		b.putString(Router.NET_INTF_NAME, net.intfName);
		b.putString(Router.NET_ADDR, net.addr);
		return b;
	}

	public static Bundle netArray2Bundle(NetInfo[] nets) {
		Bundle b = new Bundle();
		if (nets != null && nets.length > 0) {
			int numNets = nets.length;
			int[] types = new int[numNets];
			String[] names = new String[numNets];
			String[] passes = new String[numNets];
			String[] infos = new String[numNets];
			String[] intfNames = new String[numNets];
			String[] addrs = new String[numNets];
			int num = 0;
			for (NetInfo net : nets) {
				types[num] = net.type;
				names[num] = net.name;
				passes[num] = net.pass;
				infos[num]	 = new String(net.info);
				intfNames[num] = net.intfName;
				addrs[num++] = net.addr;
			}
			b.putIntArray(Router.NET_TYPES, types);
			b.putStringArray(Router.NET_NAMES, names);
			b.putStringArray(Router.NET_PASSES, passes);
			b.putStringArray(Router.NET_INFOS, infos);
			b.putStringArray(Router.NET_INTF_NAMES, intfNames);
			b.putStringArray(Router.NET_ADDRS, addrs);
		}
		return b;
	}


	// convert DeviceInfo & DeviceInfo[] to Bundle
	public static Bundle device2Bundle(DeviceInfo device) {
		Bundle b = new Bundle();
		b.putString(Router.PEER_NAME, device.name);
		b.putString(Router.PEER_ADDR, device.addr);
		b.putString(Router.PEER_PORT, device.port);
		return b;
	}

	public static Bundle deviceArray2Bundle(DeviceInfo[] devices) {
		Bundle b = new Bundle();
		if (devices != null && devices.length > 0) {
			int numDevices = devices.length;
			String[] names = new String[numDevices];
			String[] addrs = new String[numDevices];
			String[] ports = new String[numDevices];
			int num = 0;
			for (DeviceInfo dev : devices) {
				names[num] = dev.name;
				addrs[num] = dev.addr;
				ports[num++] = dev.port;
			}
			b.putStringArray(Router.PEER_NAMES, names);
			b.putStringArray(Router.PEER_ADDRS, addrs);
			b.putStringArray(Router.PEER_PORTS, ports);
		}
		return b;
	}

	public final static String[] audioDirs = new String[] { "Music",
			"music", "Songs", "songs", "Audio", "audio" };

	public static File getAudioDir() {
		File sdcard = Environment.getExternalStorageDirectory();
		StringBuilder b = new StringBuilder();
		File audioDir = null;
		for (String d : audioDirs) {
			b.setLength(0);
			b.append(sdcard.getAbsolutePath()).append("/").append(d);
			audioDir = new File(b.toString());
			if (audioDir.exists())
				return audioDir;
		}
		//if no existing audio/music dir, create the default one
		b.setLength(0);
		b.append(sdcard.getAbsolutePath()).append("/").append("Music");
		audioDir = new File(b.toString());
		audioDir.mkdirs();
		return audioDir;
	}


	public final static String[] downloadDirs = new String[] { "download",
			"Download", "downloads", "Downloads" };

	public static File getDownloadDir() {
		File sdcard = Environment.getExternalStorageDirectory();
		StringBuilder b = new StringBuilder();
		File downloadDir = null;
		for (String d : downloadDirs) {
			b.setLength(0);
			b.append(sdcard.getAbsolutePath()).append("/").append(d);
			downloadDir = new File(b.toString());
			if (downloadDir.exists())
				return downloadDir;
		}
		//if no existing download dir, create the default one
		b.setLength(0);
		b.append(sdcard.getAbsolutePath()).append("/").append("Download");
		downloadDir = new File(b.toString());
		downloadDir.mkdirs();
		return downloadDir;
	}

	// marshaling utils

	public static byte[] marshallDeviceInfo(final DeviceInfo device) {
		final Parcel parcel = Parcel.obtain();
		byte[] data = null;
		device.writeToParcel(parcel, 0);
		data = parcel.marshall();
		parcel.recycle();
		return data;
	}

	public static DeviceInfo unmarshallDeviceInfo(byte[] data, int len) {
		DeviceInfo device = null;
		final Parcel parcel = Parcel.obtain();
		parcel.unmarshall(data, 0, len);
		parcel.setDataPosition(0);
		device = new DeviceInfo(parcel);
		parcel.recycle();
		return device;
	}


	public static byte[] marshallDeviceInfoSSL(final DeviceInfo device, final boolean ssl) {
		final Parcel parcel = Parcel.obtain();
		byte[] data = null;
		parcel.writeByte((byte)(ssl?1:0));
		device.writeToParcel(parcel, 0);
		data = parcel.marshall();
		parcel.recycle();
		return data;
	}

	public static Pair<DeviceInfo, Boolean> unmarshallDeviceInfoSSL(byte[] data, int len) {
		DeviceInfo device = null;
		final Parcel parcel = Parcel.obtain();
		parcel.unmarshall(data, 0, len);
		parcel.setDataPosition(0);
		boolean useSSL = parcel.readByte() == 1;
		device = new DeviceInfo(parcel);
		parcel.recycle();
		return new Pair<DeviceInfo, Boolean>(device, useSSL);
	}

	public static byte[] marshallIntent(final Intent intent) {
		final Parcel parcel = Parcel.obtain();
		byte[] data = null;
		intent.writeToParcel(parcel, 0);
		data = parcel.marshall();
		parcel.recycle();
		return data;
	}

	public static Intent unmarshallIntent(byte[] data, int len) {
		final Parcel parcel = Parcel.obtain();
		parcel.unmarshall(data, 0, len);
		parcel.setDataPosition(0);
		/*
		 * Intent intent = new Intent(); intent.readFromParcel(parcel);
		 */
		Intent intent = (Intent) parcel.readParcelable(Intent.class
				.getClassLoader());
		parcel.recycle();
		return intent;
	}

	public static byte[] marshallBundle(final Bundle bundle) {
		final Parcel parcel = Parcel.obtain();
		byte[] data = null;
		parcel.writeBundle(bundle);
		data = parcel.marshall();
		parcel.recycle();
		return data;
	}

	public static Bundle unmarshallBundle(byte[] data, int len) {
		Bundle bundle = null;
		final Parcel parcel = Parcel.obtain();
		parcel.unmarshall(data, 0, len);
		parcel.setDataPosition(0);
		bundle = parcel.readBundle();
		parcel.recycle();
		return bundle;
	}

	@TargetApi(8)
	public static String marshallBundleBase64(final Bundle bundle) {
		String base64 = null;
		final Parcel parcel = Parcel.obtain();
		try {
			parcel.writeBundle(bundle);
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			final GZIPOutputStream zos = new GZIPOutputStream(
					new BufferedOutputStream(bos));
			zos.write(parcel.marshall());
			zos.close();
			base64 = Base64.encodeToString(bos.toByteArray(), 0);
		} catch (IOException e) {
			e.printStackTrace();
			base64 = null;
		} finally {
			parcel.recycle();
		}
		return base64;
	}

	@TargetApi(8)
	public static Bundle unmarshallBundleBase64(final String base64) {
		Bundle bundle = null;
		final Parcel parcel = Parcel.obtain();
		try {
			final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
			final byte[] buffer = new byte[1024];
			final GZIPInputStream zis = new GZIPInputStream(
					new ByteArrayInputStream(Base64.decode(base64, 0)));
			int len = 0;
			while ((len = zis.read(buffer)) != -1) {
				byteBuffer.write(buffer, 0, len);
			}
			zis.close();
			parcel.unmarshall(byteBuffer.toByteArray(), 0, byteBuffer.size());
			parcel.setDataPosition(0);
			bundle = parcel.readBundle();
		} catch (IOException e) {
			e.printStackTrace();
			bundle = null;
		} finally {
			parcel.recycle();
		}

		return bundle;
	}

}
