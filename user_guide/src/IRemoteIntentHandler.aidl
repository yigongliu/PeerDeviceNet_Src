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

interface IRemoteIntentHandler {
	oneway void onError(in String errInfo);
	oneway void onResult(in Intent result);
	oneway void onStart(int sessionId);
	oneway void onProgress(int percent); 
	oneway void onComplete();
	oneway void onRejected();
	oneway void onCanceled();
}
