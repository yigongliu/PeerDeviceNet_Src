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


/* ------- external peerdevicenet router API ------
 * define the following:
 * 1. intent action names for group messaging
 * 2. message ids or tags used for messenger and aidl api
 * 3. standard key names for message content which is a bundle or hash
 */
public class Router {
	//service startup intents
	//for starting router service
	public static final String ACTION_SERVICE = "com.xconns.peerdevicenet.Service";
	public static final String ACTION_CONNECTION_SERVICE = "com.xconns.peerdevicenet.ConnectionService";
	public static final String ACTION_GROUP_SERVICE = "com.xconns.peerdevicenet.GroupService";
	public static final String ACTION_MESSENGER_SERVICE = "com.xconns.peerdevicenet.Messenger";
	//for starting conn mgr service
	public static final String ACTION_CONNECTOR = "com.xconns.peerdevicenet.CONNECTOR";
	public static final String ACTION_CONNECTION_MANAGEMENT = "com.xconns.peerdevicenet.CONNECTION_MANAGEMENT";
	//for starting remote intent service
	public static final String ACTION_REMOTE_INTENT_SERVICE = "com.xconns.peerdevicenet.RemoteIntentService";

	//life-cycle events
	public static final String ACTION_ROUTER_STARTUP = "com.xconns.peerdevicenet.Startup";
	public static final String ACTION_ROUTER_RESET = "com.xconns.peerdevicenet.Reset";
	public static final String ACTION_ROUTER_SHUTDOWN = "com.xconns.peerdevicenet.Shutdown";

	// intents for msg-passing msg ids
	public static final String ACTION_ERROR = "com.xconns.peerdevicenet.ERROR";
	public static final String ACTION_START_SEARCH = "com.xconns.peerdevicenet.START_SEARCH";
	public static final String ACTION_STOP_SEARCH = "com.xconns.peerdevicenet.STOP_SEARCH";
	public static final String ACTION_SEARCH_START = "com.xconns.peerdevicenet.SEARCH_START";
	public static final String ACTION_SEARCH_FOUND_DEVICE = "com.xconns.peerdevicenet.SEARCH_FOUND_DEVICE";
	public static final String ACTION_SEARCH_COMPLETE = "com.xconns.peerdevicenet.SEARCH_COMPLETE";
	public static final String ACTION_CONNECT = "com.xconns.peerdevicenet.CONNECT";
	public static final String ACTION_DISCONNECT = "com.xconns.peerdevicenet.DISCONNECT";
	public static final String ACTION_ACCEPT_CONNECTION = "com.xconns.peerdevicenet.ACCEPT_CONNECTION";
	public static final String ACTION_DENY_CONNECTION = "com.xconns.peerdevicenet.DENY_CONNECTION";
	public static final String ACTION_CONNECTING = "com.xconns.peerdevicenet.CONNECTING";
	public static final String ACTION_CONNECTION_FAILED = "com.xconns.peerdevicenet.CONNECTION_FAILED";
	public static final String ACTION_CONNECTED = "com.xconns.peerdevicenet.CONNECTED";
	public static final String ACTION_DISCONNECTED = "com.xconns.peerdevicenet.DISCONNECTED";
	public static final String ACTION_JOIN_GROUP = "com.xconns.peerdevicenet.JOIN_GROUP";
	public static final String ACTION_LEAVE_GROUP = "com.xconns.peerdevicenet.LEAVE_GROUP";
	public static final String ACTION_SELF_JOIN = "com.xconns.peerdevicenet.SELF_JOIN";
	public static final String ACTION_PEER_JOIN = "com.xconns.peerdevicenet.PEER_JOIN";
	public static final String ACTION_SELF_LEAVE = "com.xconns.peerdevicenet.SELF_LEAVE";
	public static final String ACTION_PEER_LEAVE = "com.xconns.peerdevicenet.PEER_LEAVE";
	public static final String ACTION_SEND_MSG = "com.xconns.peerdevicenet.SEND_MSG";
	public static final String ACTION_RECV_MSG = "com.xconns.peerdevicenet.RECV_MSG";
	public static final String ACTION_SET_CONNECTION_INFO = "com.xconns.peerdevicenet.SET_CONNECTION_INFO";
	public static final String ACTION_GET_CONNECTION_INFO = "com.xconns.peerdevicenet.GET_CONNECTION_INFO";
	public static final String ACTION_GET_DEVICE_INFO = "com.xconns.peerdevicenet.GET_DEVICE_INFO";
	public static final String ACTION_GET_CONNECTED_PEERS = "com.xconns.peerdevicenet.GET_CONNECTED_PEERS";
	public static final String ACTION_GET_NETWORKS = "com.xconns.peerdevicenet.GET_NETWORKS";
	public static final String ACTION_GET_ACTIVE_NETWORK = "com.xconns.peerdevicenet.GET_ACTIVE_NETWORK";
	public static final String ACTION_ACTIVATE_NETWORK = "com.xconns.peerdevicenet.ACTIVATE_NETWORK";
	public static final String ACTION_NETWORK_CONNECTED = "com.xconns.peerdevicenet.NETWORK_CONNECTED";
	public static final String ACTION_NETWORK_DISCONNECTED = "com.xconns.peerdevicenet.NETWORK_DISCONNECTED";

	//remote intent related
	public static final String ACTION_START_REMOTE_ACTIVITY = "com.xconns.peerdevicenet.START_REMOTE_ACTIVITY";
	public static final String ACTION_START_REMOTE_ACTIVITY_FOR_RESULT = "com.xconns.peerdevicenet.START_REMOTE_ACTIVITY_FOR_RESULT";
	public static final String ACTION_START_REMOTE_SERVICE = "com.xconns.peerdevicenet.START_REMOTE_SERVICE";
	public static final String ACTION_SEND_REMOTE_BROADCAST = "com.xconns.peerdevicenet.SEND_REMOTE_BROADCAST";
	
	// Message tags for local msging peers
	public final static class MsgId {
		// connection msgs
		public final static int ERROR = -10100;

		//cmds
		public final static int START_SEARCH = -10200;
		public final static int STOP_SEARCH = -10201;
		//state change
		public final static int SEARCH_START = -10210;
		public final static int SEARCH_FOUND_DEVICE = -10211;
		public final static int SEARCH_COMPLETE = -10212;
		
		//cmds
		public final static int CONNECT = -10300;
		public final static int DISCONNECT = -10301;
		public final static int ACCEPT_CONNECTION = -10302;
		public final static int DENY_CONNECTION = -10303;
		//state change
		public final static int CONNECTING = -10310;
		public final static int CONNECTION_FAILED = -10311;
		public final static int CONNECTED = -10312;
		public final static int DISCONNECTED = -10313;

		//cmds
		public final static int JOIN_GROUP = -10400;
		public final static int LEAVE_GROUP = -10401;
		//state change
		public final static int SELF_JOIN = -10410;
		public final static int PEER_JOIN = -10411;
		public final static int SELF_LEAVE = -10412;
		public final static int PEER_LEAVE = -10413;
		
		//
		public final static int SEND_MSG = -10500;
		public final static int RECV_MSG = -10510;
		
		//
		public final static int SET_CONNECTION_INFO = -10600;
		public final static int GET_CONNECTION_INFO = -10601;
		public final static int GET_DEVICE_INFO = -10602;
		public final static int GET_CONNECTED_PEERS = -10603;
		
		//
		public static final int GET_NETWORKS = 10700;
		public static final int GET_ACTIVE_NETWORK = 10701;
		public static final int ACTIVATE_NETWORK = 10702;
		public static final int NETWORK_CONNECTED = 10703;
		public static final int NETWORK_DISCONNECTED = 10704;
		//
		public final static int REGISTER_RECEIVER = -10800;
		public final static int UNREGISTER_RECEIVER = -10801;
		
		//
		public final static int START_REMOTE_ACTIVITY = -10900;
		public final static int START_REMOTE_SERVICE = -10901;
		public final static int SEND_REMOTE_BROADCAST = -10902;
	}
	
	public final static class ConnFailureCode {
		public final static int FAIL_CONNMGR_INACTIVE = 1;
		public final static int FAIL_PIN_MISMATCH = 2;
		public final static int FAIL_CONN_EXIST = 3;
		public final static int FAIL_REJECT_BY_USER = 4;
		public final static int FAIL_UNKNOWN_PEER = 5;
		public final static int FAIL_CONN_SELF = 6;
		public final static int FAIL_LOSE_WIFI = 7;
		public final static int FAIL_LOSE_CONNECTION = 8;
	}

	// msg data bundle keys
	public static final String MSG_ID = "MSG_ID";
	public static final String MSG_DATA = "MSG_DATA";
	public static final String PEER_NAME = "PEER_NAME";
	public static final String PEER_ADDR = "PEER_ADDR";
	public static final String PEER_PORT = "PEER_PORT";
	public static final String DEVICE_NAME = "DEVICE_NAME";
	public static final String DEVICE_ADDR = "DEVICE_ADDR";
	public static final String DEVICE_PORT = "DEVICE_PORT";
	// multicast groups; use string group_id to dispatch msgs to group peers
	public static final String GROUP_ID = "GROUP_ID";
	// id for data array
	public static final String PEER_NAMES = "PEER_NAMES";
	public static final String PEER_ADDRS = "PEER_ADDRS";
	public static final String PEER_PORTS = "PEER_PORTS";
	//keys for net info
	public static final String NET_TYPE = "NET_TYPE";
	public static final String NET_NAME = "NET_NAME";
	public static final String NET_ENCRYPT = "NET_ENCRYPT";
	public static final String NET_PASS = "NET_PASS";
	public static final String NET_HIDDEN = "NET_HIDDEN";
	public static final String NET_INFO = "NET_INFO";
	public static final String NET_INTF_NAME = "NET_INTF_NAME";
	public static final String NET_ADDR = "NET_ADDR";
	public static final String NET_INTF_MCAST = "NET_INTF_MCAST";

	public static final String NET_TYPES = "NET_TYPES";
	public static final String NET_NAMES = "NET_NAMES";
	public static final String NET_ENCRYPTS = "NET_ENCRYPTS";
	public static final String NET_PASSES = "NET_PASSES";
	public static final String NET_HIDDENS = "NET_HIDDENS";
	public static final String NET_INFOS = "NET_INFOS";
	public static final String NET_INTF_NAMES = "NET_INTF_NAMES";
	public static final String NET_ADDRS = "NET_ADDRS";

	//
	public static final String TIMEOUT = "TIMEOUT";
	public static final String LIVENESS_TIMEOUT = "LIVENESSTIMEOUT";
	public static final String CONNECT_TIMEOUT = "CONNECT_TIMEOUT";
	public static final String SEARCH_TIMEOUT = "SEARCH_TIMEOUT";
	public static final String USE_SSL = "USE_SSL";
	public static final String AUTHENTICATION_TOKEN = "AUTHENTICATION_TOKEN";
	public static final String CONN_DENY_CODE = "CONNECTION_DENY_CODE";
	//remote intent bundle keys
	public static final String ACTION = "ACTION";
	public static final String TYPE = "TYPE";
	public static final String URI = "URI";
	public static final String URIS = "URIS";
	public static final String EXTRAS = "EXTRAS";
	public static final String REMOTE_INTENT = "REMOTE_INTENT";
	public static final String PACKAGE_NAME = "PACKAGE_NAME";
	
	//translate msg id into string for printout
	public static String MsgName(int msgId) {
		switch (msgId) {
		case MsgId.ERROR:
			return "ERROR";
		case MsgId.START_SEARCH:
			return "START_SEARCH";
		case MsgId.STOP_SEARCH:
			return "STOP_SEARCH";
		case MsgId.SEARCH_START:
			return "SEARCH_START";
		case MsgId.SEARCH_FOUND_DEVICE:
			return "SEARCH_FOUND_DEVICE";
		case MsgId.SEARCH_COMPLETE:
			return "SEARCH_COMPLETE";
		case MsgId.ACCEPT_CONNECTION:
			return "ACCEPT_CONNECTION";
		case MsgId.DENY_CONNECTION:
			return "DENY_CONNECTION";
		case MsgId.CONNECTING:
			return "CONNECTING";
		case MsgId.CONNECTION_FAILED:
			return "CONNECTION_FAILED";
		case MsgId.CONNECT:
			return "CONNECT";
		case MsgId.DISCONNECT:
			return "DISCONNECT";
		case MsgId.CONNECTED:
			return "CONNECTED";
		case MsgId.DISCONNECTED:
			return "DISCONNECTED";
		case MsgId.JOIN_GROUP:
			return "JOIN_GROUP";
		case MsgId.LEAVE_GROUP:
			return "LEAVE_GROUP";
		case MsgId.SELF_JOIN:
			return "SELF_JOIN";
		case MsgId.PEER_JOIN:
			return "PEER_JOIN";
		case MsgId.SELF_LEAVE:
			return "SELF_LEAVE";
		case MsgId.PEER_LEAVE:
			return "PEER_LEAVE";
		case MsgId.SEND_MSG:
			return "SEND_MSG";
		case MsgId.RECV_MSG:
			return "RECV_MSG";
		case MsgId.SET_CONNECTION_INFO:
			return "SET_CONNECTION_INFO";
		case MsgId.GET_CONNECTION_INFO:
			return "GET_CONNECTION_INFO";
		case MsgId.GET_DEVICE_INFO:
			return "GET_DEVICE_INFO";
		case MsgId.GET_CONNECTED_PEERS:
			return "GET_CONNECTED_PEERS";
		case MsgId.GET_NETWORKS:
			return "GET_NETWORKS";
		case MsgId.GET_ACTIVE_NETWORK:
			return "GET_ACTIVE_NETWORK";
		case MsgId.ACTIVATE_NETWORK:
			return "ACTIVATE_NETWORK";
		case MsgId.NETWORK_CONNECTED:
			return "ETWORK_CONNECTED";
		case MsgId.NETWORK_DISCONNECTED:
			return "ETWORK_DISCONNECTED";
		case MsgId.REGISTER_RECEIVER:
			return "REGISTER_RECEIVER";
		case MsgId.UNREGISTER_RECEIVER:
			return "UNREGISTER_RECEIVER";
		case MsgId.START_REMOTE_ACTIVITY:
			return "START_REMOTE_ACTIVITY";
		case MsgId.START_REMOTE_SERVICE:
			return "START_REMOTE_SERVICE";
		case MsgId.SEND_REMOTE_BROADCAST:
			return "SEND_REMOTE_BROADCAST";
		}
		return Integer.toString(msgId);
	}

}
