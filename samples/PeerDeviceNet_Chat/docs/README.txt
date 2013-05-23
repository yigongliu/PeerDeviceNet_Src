This sample demonstrates how to write chat apps using PeerDeviceNet 
group communication APIs. There is a chat app implementation for each kind of APIs:
   . intenting API: ChatByIntentingActivity.java
   . Messenger API: ChatByMessengerActivity.java
   . IDL API:       ChatByAidlActivity.java

In AndroidManifest.xml, add the following permission to enable group communication:
  <uses-permission android:name="com.xconns.peerdevicenet.permission.REMOTE_MESSAGING" />

ChatActivity.java defines a main menu allowing you choose a specific chat implementation.

The first menu entry allows you reuse PeerDeviceNet connection manager to connect 
your device with other devices. Its callback function has the following code
to bring up connection manager:
	Intent intent = new Intent("com.xconns.peerdevicenet.CONNECTION_MANAGEMENT");
	startActivity(intent);

All three chat apps have two components:
1> GUI - a ListView showing the conversations among peers; a text box to enter new messages.
		 all three chat apps share the same GUI and interaction logic.
2> chat message plumbing - how to send the message user entered to peers;
		how to receive messages from peers and show it in conversations ListView.
		These three apps use different plumbing.
		
All devices participating in chat will join a group named "WifiChat".		

1. Message plumbing using "intenting" API (ChatByIntentingActivity.java).

   The "intenting" API works by sending application messages as intents to GROUP_SERVICE, and
   receive messages as broadcast intents. Intent actions names are used as message "ids / types" 
   to distinguish application messages. All application message data are passed as 
   "extra" data items in intents and all extra data key names are defined in Router.java. 
   We are using startService() to send intents to group service even if it has been started.
   
   1.1 Setup group communication with Activity's life-cycle:
   		onCreate():
   		   first we register to handle broadcast intents for receiving messages and group events such as
   		   peer join or leave:
		   		IntentFilter filter = new IntentFilter();
				filter.addAction(Router.ACTION_RECV_MSG);
				filter.addAction(Router.ACTION_SELF_JOIN);
				filter.addAction(Router.ACTION_PEER_JOIN);
				filter.addAction(Router.ACTION_PEER_LEAVE);
				filter.addAction(Router.ACTION_ERROR);
				registerReceiver(mReceiver, filter);
		   then send intent to join group named "WifiChat":
		   		Intent intent = new Intent(Router.ACTION_JOIN_GROUP);
				intent.putExtra(Router.GROUP_ID, groupId);
				startService(intent);
   		   please note that the group id is passed as "extra" data item. 
   		   
    1.2 Tear down group communication with Activity's life-cycle:
   		onDestroy():
   			here we send intent to leave the chat group and unregister broadcast intents:
   			Intent intent = new Intent(Router.ACTION_LEAVE_GROUP);
			intent.putExtra(Router.GROUP_ID, groupId);
			startService(intent);
			//
			unregisterReceiver(mReceiver);
			
   1.3 Send message:
   		At bottom of screen is a text box to allow user enter new messages.
   		The "Send" button callback will collect user message from this text box and send it as following intent:
   		sendMsg(String msg):
	   		Intent intent = new Intent(Router.ACTION_SEND_MSG);
			intent.putExtra(Router.GROUP_ID, groupId);
			intent.putExtra(Router.MSG_DATA, msg.getBytes());
			startService(intent);
			
   1.4 Receive Message:
   		The registered broadcast receiver will override onReceive() method to receive application messages 
   		and group events, process them based on intent action names (or message types) and display them in ListView.
   		Please note that application messages contents are passed as "extra" data items with the following keys:
   		. Router.PEER_NAME(S)/PEER_ADDR(S)/PEER_PORT(S): info about peer devices, used for PEER_JOIN/LEAVE events.
   		. Router.MSG_DATA: chat messages.
   		
   		mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (Router.ACTION_SELF_JOIN.equals(action)) {
				//first time when i join the group, find out info about existing peers
			} else if (Router.ACTION_PEER_JOIN.equals(action)) {
				//a new peer joins, show its name/addr
			} else if (Router.ACTION_PEER_LEAVE.equals(action)) {
				//a peer left, show a message
			} else if (Router.ACTION_RECV_MSG.equals(action)) {
				//a peer sent me a message, display the message prefixed by peer's name
			} else if (Router.ACTION_ERROR.equals(action)) {
				//something went wrong, show error message
			}
		}

2. Message plumbing using Messenger API (ChatByMessengerActivity.java).
	
	Messenger API follows android's internal messaging pattern "Messenger" and extends it across devices.
	The app code is similar to normal "Messenger" based app. A messenger is returned when binding to
	service and it is used to send messages. Another messenger is registered with service to receive
	messages. Application messages are distinguished by integer message ids defined in Router.java. 
	Application data are 
	passed as a bundle with data items indexed using key names defined in Router.java.
	
	2.1 group communication setup during Activity life-cycle event.
		onCreate():
		  bind to messenger service inside doBindService():
				Intent intent = new Intent("com.xconns.peerdevicenet.Messenger");
				bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
			
		onServiceConnected():
		  once bound to service, register a receiver messenger to receive messages:
				Message msg = Message.obtain(null, Router.MsgId.REGISTER_RECEIVER);
				msg.replyTo = mMessenger;
				mService.send(msg);
		  then join group
			  	Bundle b = new Bundle();
				b.putString(Router.GROUP_ID, groupId);
				Message msg = Message.obtain(null, Router.MsgId.JOIN_GROUP);
				msg.setData(b);
				mService.send(msg);
			
	2.2 group communication tear-down during Activity life-cycle event.
		onDestroy():
		  doUnbindService():
		  	first leave chat group:
			  	Bundle b = new Bundle();
				b.putString(Router.GROUP_ID, groupId);
				Message msg = Message.obtain(null, Router.MsgId.LEAVE_GROUP);
				msg.setData(b);
				mService.send(msg);
			
			then unregister receiver messenger:
				msg = Message.obtain(null, Router.MsgId.UNREGISTER_RECEIVER);
				msg.replyTo = mMessenger;
				mService.send(msg);

			finally unbind messenger service
				unbindService(mConnection);
				
	2.3 send messages.
		when sending a chat message to peers, pack the message and group id inside a bundle; wrap
		the bundle inside a Message object with id = Router.MsgId.SEND_MSG; and send it thru
		messenger.
		sendMsg(String msg_data):
			Message msg = Message.obtain(null, Router.MsgId.SEND_MSG, 0, 0);
			Bundle b = new Bundle();
			b.putByteArray(Router.MSG_DATA, msg_data.getBytes());
			b.putString(Router.GROUP_ID, groupId);
			msg.setData(b);
			mService.send(msg);
			
	2.4 receive message.
		a receiver messenger is defined with a handler (IncomingHandler) to handle received messages:
			mMessenger = new Messenger(new IncomingHandler());
		IncomingHandler will override its handleMessage() method to process group events and display
			chat message in conversation ListView.

3. Message plumbing using AIDL API (ChatByAidlActivity.java).
	AIDL API follows android's IDL IPC model and extends it across devices. To use this API,
	apps need to add PeerDeviceNet group communication related IDL files to project and bind to
	group service. Since AIDL API methods are all asynchronous, we'll register a group handler
	to handle received messages and group events.

	3.1 Add the following aidl files under package com.xconns.peerdevicenet:
		DeviceInfo.java - a simple class containing info about device: name, address, port
		DeviceInfo.aidl
 		IRouterGroupService.aidl - async calls to join/leave group and send messages
 		IRouterGroupHandler.aidl - callback interface to receive messages and group events 
 									such as peer join/leave.
 		Router.java - optionally included for convenience, define commonly used message ids;
 					  normally used for Intent based and Messenger based APIs; used here to 
 					  convert IDL callbacks into Messages handled by GUI handler.

	3.2 group communication setup during Activity life-cycle
		onCreate():
		  here we bind to idl group service
			Intent intent = new Intent("com.xconns.peerdevicenet.GroupService");
			bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
			
		onServiceConnected():
		  after service bound, we join group and register group handler:
				mGroupService.joinGroup(groupId, null, mGroupHandler);
	
	3.2 group communication tear-down during Activity life-cycle
		onDestroy():
		  here we leave group, unregister group handler and unbind service:
		  	mGroupService.leaveGroup(groupId, mGroupHandler);
			unbindService(mConnection);
	
	3.3 Send messages
		call service method to send message:
			mGroupService.send(groupId, null, msg_data.getBytes());
	
	3.4 Receive messages
		a group handler is defined and registered with service to receive peer messages
		and group events.
		
		mGroupHandler = new IRouterGroupHandler.Stub() {
			public void onError(String errInfo) throws RemoteException {
				//process error messages
			}
			public void onSelfJoin(DeviceInfo[] devices) throws RemoteException {
				//the first time i join group, "devices" are current peers in group
			}
			public void onPeerJoin(DeviceInfo device) throws RemoteException {
				//a new peer device joined the group
			}
			public void onSelfLeave() throws RemoteException {
			}
			public void onPeerLeave(DeviceInfo device) throws RemoteException {
				//process the event that a peer device just left group
			}
			public void onReceive(DeviceInfo src, byte[] b) throws RemoteException {
				//process message "b" (in byte array) from peer device "src"
			}
			public void onGetPeerDevices(DeviceInfo[] devices)
					throws RemoteException {
				//response message to query
			}
		};
			
		Please note that group handler's methods are executed in a thread pool
		managed by android runtime, while GUI changes should be done in main GUI thread.
		So create a android.os.Handler object (mHandler) with GUI thread; and the above 
		group handler methods will forward event message to this handler object for processing.
