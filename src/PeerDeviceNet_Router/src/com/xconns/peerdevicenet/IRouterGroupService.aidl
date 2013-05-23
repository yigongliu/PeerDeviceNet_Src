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

import com.xconns.peerdevicenet.IRouterGroupHandler;
import com.xconns.peerdevicenet.DeviceInfo;
import android.os.Bundle;

/* pure async api */
interface IRouterGroupService {
	oneway void joinGroup(String groupId, in DeviceInfo[] peers, in IRouterGroupHandler h);
	oneway void leaveGroup(String groupId, in IRouterGroupHandler h);
	oneway void send(String groupId, in DeviceInfo dest, in byte[] msg);
	oneway void getPeerDevices(String groupId);
}
