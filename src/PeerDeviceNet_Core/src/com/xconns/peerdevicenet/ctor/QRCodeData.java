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

package com.xconns.peerdevicenet.ctor;

import com.xconns.peerdevicenet.NetInfo;

public class QRCodeData {
	public String ssid;
	public String passwd;
	public int encrypt;
	public boolean hidden;
	public boolean useSSL;
	public String addr;
	public QRCodeData(String s, String p, int t, boolean h, boolean u, String a) {
		ssid = s;
		passwd = p;
		encrypt = t;
		hidden = h;
		useSSL = u;
		addr = a;
	}
	public String toString() {
		return String.format("ssid:%s; passwd:%s; encrypt:%s; hidden:%s, useSSL:%s, addr:%s", ssid, passwd, NetInfo.NetEncryptionName(encrypt), hidden, useSSL, addr);		
	}
	public String toText() {
		return String.format("ssid:%s; passwd:%s; encrypt:%s", ssid, passwd, NetInfo.NetEncryptionName(encrypt));						
	}
	public final String encode() {
		String pass = passwd;
		if(encrypt != NetInfo.NoPass && passwd.equals("*")) {
			pass = ""; //force checking configured nets
		}
		return String.format("%s~=>%s~=>%s~=>%s~=>%s~=>%s", ssid, pass, NetInfo.NetEncryptionName(encrypt), hidden, useSSL, addr);
	}
	public final static QRCodeData decode(String r) {
		String[] rs = r.split("~=>");
		if (rs != null && rs.length >= 4) {
			int enc = NetInfo.NoPass;
			if ("WPA".equals(rs[2])) {
				enc = NetInfo.WPA;
			}
			else if("WEP".equals(rs[2])) {
				enc = NetInfo.WEP;
			}
			boolean h = false;
			if ("true".equals(rs[3])) h = true;
			boolean useSSL = false;
			if ("true".equals(rs[4])) useSSL = true;
			String addr = rs[5];
			return new QRCodeData(rs[0], rs[1], enc, h, useSSL, addr);
		}
		return null;
	}
}
