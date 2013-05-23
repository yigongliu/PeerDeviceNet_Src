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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import android.content.Context;
import android.util.Log;

public class PlainSocketFactory implements SocketFactory {
	public final static String TAG = "PlainSocketFactory";
	private static volatile PlainSocketFactory instance = null;
	private static volatile Object lock = new Object();
	
	protected PlainSocketFactory(Context c) {
	}
	
	public static PlainSocketFactory getInstance(Context c) {
		if (instance == null) {
			synchronized (lock) {
				if (instance == null) {
					instance = new PlainSocketFactory(c);
				}
			}
		}
		Log.d(TAG, "PlainSocketFactory instance returned");
		return instance;
	}
	
	public ServerSocket newServerSocket() {
		ServerSocket sock = null;
		try {
			sock = new ServerSocket();
		} catch(IOException ioe) {
			Log.d(TAG, ioe.getMessage());
		}
		return sock;
	}
	
	public ServerSocket newServerSocket(int port) {
		ServerSocket sock = null;
		try {
			sock = new ServerSocket(port);
		} catch(IOException ioe) {
			Log.d(TAG, ioe.getMessage());
		}
		return sock;
	}
	
	public Socket newClientSocket() {
		return new Socket();
	}
}
