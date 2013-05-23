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

import android.os.Parcel;
import android.os.Parcelable;

public class NetInfo implements Parcelable {
	//network type names
	public final static int NoNet = -1;
	public final static int WiFi = 0;
	public final static int WiFiDirect = 1;
	public final static int WiFiHotspot = 2;
	public final static int Bluetooth = 3;
	public final static int Mobile = 4;
	public final static int Cloud = 5;
	public final static int Other = 6;
	public final static int NET_TYPES = 7;
	//network encryption name
	public final static int NoPass = 0;
	public final static int WPA = 1;
	public final static int WEP = 2;
	
	public final static String NetTypeName(int t) {
		switch (t) {
		case NoNet:
			return "NoNet";
		case WiFi:
			return "Wi-Fi";
		case WiFiDirect:
			return "Wi-Fi Direct";
		case WiFiHotspot:
			return "Wi-Fi Hotspot";
		case Bluetooth:
			return "Bluetooth";
		case Mobile:
			return "Mobile";
		case Cloud:
			return "Cloud";
		case Other:
			return "Other";
		}
		return null;
	}
	
	public final static String NetEncryptionName(int t) {
		switch (t) {
		case NoPass:
			return "NoPass";
		case WPA:
			return "WPA";
		case WEP:
			return "WEP";
		}
		return null;
	}
	
	//net info
	public int type = NoNet;
	public String name = null;
	public int encrypt = NoPass;
	public String pass = null;
	public boolean hidden = false;
	public byte[] info = null;
	public String intfName = null;  //local interface name for this net
	public String addr = null;  //addr of this host in this net
	public boolean mcast = false;
	
	public NetInfo(int t, String n, int enc, String p, boolean h, byte[] i, String in, String a, boolean m) {
		type = t;
		name = n;
		encrypt = enc;
		pass = p;
		hidden = h;
		info = i;
		intfName = in;
		addr = a;
		mcast = m;
	}

	public static final Creator<NetInfo> CREATOR = new Creator<NetInfo>() {
		public NetInfo[] newArray(int size) {
			return new NetInfo[size];
		}
		
		public NetInfo createFromParcel(Parcel in) {
			return new NetInfo(in);
		}
	};
	
	public NetInfo() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public String toString() {
		return String.format("type:%s, name:%s, pass:%s, intfName:%s, addr:%s, mcast:%b", type, name, pass, intfName, addr, mcast);
	}

	public int describeContents() {
		return 0;
	}

	public NetInfo(Parcel in) {
		readFromParcel(in);
	}
	
	public void readFromParcel(Parcel in) {
		type = in.readInt();
		name = in.readString();
		encrypt = in.readInt();
		pass = in.readString();
		hidden = (in.readByte()==0)?false:true;
		//in.readByteArray(info);
		info = in.createByteArray();
		intfName = in.readString();  //local interface name for this net
		addr = in.readString();  //addr of this host in this net
		mcast = (in.readByte()==0)?false:true;
	}
	
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(type);
		out.writeString(name);
		out.writeInt(encrypt);
		out.writeString(pass);
		out.writeByte((byte)(hidden?1:0));
		out.writeByteArray(info);
		out.writeString(intfName);
		out.writeString(addr);
		out.writeByte((byte)(mcast?1:0));
	}
}
