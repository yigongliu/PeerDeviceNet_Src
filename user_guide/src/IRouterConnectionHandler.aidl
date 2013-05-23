/*************************************************************************
 * 
 * XCONNS CONFIDENTIAL
 * __________________
 * 
 *  2012 - 2013  XCONNS, LLC 
 *  All Rights Reserved.
 * 
 * NOTICE:  All information contained herein is, and remains
 * the property of XCONNS, LLC and its suppliers, if any.  
 * The intellectual and technical concepts contained herein are 
 * proprietary to XCONNS, LLC and its suppliers and may be covered 
 * by U.S. and Foreign Patents, patents in process, and are protected 
 * by trade secret or copyright law. Dissemination of this information
 * or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from XCONNS, LLC.
 */

package com.xconns.peerdevicenet;

import android.os.Bundle;
import com.xconns.peerdevicenet.DeviceInfo;

interface IRouterConnectionHandler {
	oneway void onError(in String errInfo);
	oneway void onSearchFoundDevice(in DeviceInfo device);
	oneway void onSearchComplete();
	oneway void onConnecting(in DeviceInfo device, in byte[] token);
	oneway void onConnectionFailed(in DeviceInfo device, int rejectCode);  //onConnectionDenied(...)
	oneway void onConnected(in DeviceInfo device);
	oneway void onDisconnected(in DeviceInfo device);
	oneway void onSetDeviceInfo();
	oneway void onGetDeviceInfo(in DeviceInfo device);
	oneway void onGetPeerDevices(in DeviceInfo[] devices);
}
