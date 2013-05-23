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

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.IRemoteIntentHandler;

interface IRemoteIntentService {
	oneway void startRemoteActivity(in DeviceInfo device, in Intent intent, IRemoteIntentHandler h);
	oneway void startRemoteActivityForResult(in DeviceInfo device, in Intent intent, IRemoteIntentHandler h);
	oneway void startRemoteService(in DeviceInfo device, in Intent intent, IRemoteIntentHandler h);
	oneway void sendRemoteBroadcast(in DeviceInfo device, in Intent intent, IRemoteIntentHandler h);
	oneway void cancelSession(int sessionId);
}
