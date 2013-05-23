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

interface IRouterGroupHandler {
	oneway void onError(in String errInfo);
	oneway void onSelfJoin(in DeviceInfo[] devices);
	oneway void onPeerJoin(in DeviceInfo device);
	oneway void onSelfLeave();
	oneway void onPeerLeave(in DeviceInfo device);
	oneway void onReceive(in DeviceInfo src, in byte[] msg);
	oneway void onGetPeerDevices(in DeviceInfo[] devices);
}
