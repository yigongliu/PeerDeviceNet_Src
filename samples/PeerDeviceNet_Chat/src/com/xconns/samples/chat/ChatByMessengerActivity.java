package com.xconns.samples.chat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.xconns.peerdevicenet.Router;

public class ChatByMessengerActivity extends Activity {
	// Debugging
	private static final String TAG = "ChatByMessengerActivity";

	/** Messenger for communicating with service. */
	Messenger mService = null;

	private static final String groupId = "WifiChat";

	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Router.MsgId.RECV_MSG:
				// append peer msg to chat area
				byte[] data = msg.getData().getByteArray(Router.MSG_DATA);
				String pname = msg.getData().getString(Router.PEER_NAME);
				String paddr = msg.getData().getString(Router.PEER_ADDR);
				mChatArrayAdapter.add(pname+"("+paddr+") send: " + new String(data));
				break;
			case Router.MsgId.SELF_JOIN:
				// append peer msg to chat area
				String[] pnames = msg.getData().getStringArray(
						Router.PEER_NAMES);
				String[] paddrs = msg.getData().getStringArray(
						Router.PEER_ADDRS);
				if (pnames != null && pnames.length > 0 && paddrs != null
						&& paddrs.length > 0) {
					for (int i = 0; i < pnames.length; i++) {
						mChatArrayAdapter.add("Peer join : " + pnames[i] + ":"
								+ paddrs[i]);
					}
					numPeer = pnames.length;
					Log.d(TAG, "self_join found peers: " + numPeer);
					mSendButton.setEnabled(true);
				}
				break;
			case Router.MsgId.PEER_JOIN:
				// append peer msg to chat area
				pname = msg.getData().getString(Router.PEER_NAME);
				paddr = msg.getData().getString(Router.PEER_ADDR);
				if (pname != null && paddr != null) {
					mChatArrayAdapter.add("Peer join : " + pname + ":" + paddr);
					numPeer++;
					mSendButton.setEnabled(true);
				}
				break;
			case Router.MsgId.PEER_LEAVE:
				// append peer msg to chat area
				pname = msg.getData().getString(Router.PEER_NAME);
				paddr = msg.getData().getString(Router.PEER_ADDR);
				if (pname != null && paddr != null) {
					mChatArrayAdapter
							.add("Peer leave : " + pname + ":" + paddr);
					numPeer--;
					if (numPeer <= 0) {
						numPeer = 0;
						mSendButton.setEnabled(false);
					}
				}
				break;
			case Router.MsgId.ERROR:
				String err = msg.getData().getString(Router.MSG_DATA);
				mChatArrayAdapter.add(err);
				// Toast.makeText(ChatByIntentingActivity.this,
				// "connection failed: "+msg, Toast.LENGTH_SHORT).show();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	/**
	 * Target we publish for clients to send messages to IncomingHandler.
	 */
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			mService = new Messenger(service);
			mChatArrayAdapter.add("Service attached.");

			// register callback msnger
			try {
				Message msg = Message.obtain(null,
						Router.MsgId.REGISTER_RECEIVER);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
				// Log.e(TAG, e.getMessage());
			}

			// join group
			Bundle b = new Bundle();
			b.putString(Router.GROUP_ID, groupId);
			Message msg = Message.obtain(null, Router.MsgId.JOIN_GROUP);
			msg.setData(b);
			try {
				mService.send(msg);
			} catch (RemoteException e) {
				// Log.e(TAG, e.getMessage());
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
			mChatArrayAdapter.add("Service disconnected.");
		}
	};

	void doBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		Intent intent = new Intent("com.xconns.peerdevicenet.Messenger");
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		mChatArrayAdapter.add("Service connecting.");
	}

	void doUnbindService() {
		// leave group
		Bundle b = new Bundle();
		b.putString(Router.GROUP_ID, groupId);
		Message msg = Message.obtain(null, Router.MsgId.LEAVE_GROUP);
		msg.setData(b);
		try {
			mService.send(msg);
		} catch (RemoteException e) {
			// Log.e(TAG, e.getMessage());
		}
		// unregister callback msnger
		if (mService != null) {
			try {
				msg = Message.obtain(null, Router.MsgId.UNREGISTER_RECEIVER);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				// There is nothing special we need to do if the service
				// has crashed.
			}
		}

		// Detach our existing connection.
		unbindService(mConnection);
		mChatArrayAdapter.add("Service unbinding.");
	}

	private ArrayAdapter<String> mChatArrayAdapter;
	private ListView mChatView;
	private EditText mOutMsgText;
	private Button mSendButton;

	int numPeer = 0;

	/**
	 * Standard initialization of this activity. Set up the UI, then wait for
	 * the user to poke it before doing anything.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Initialize the array adapter for the conversation thread
		mChatArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
		mChatView = (ListView) findViewById(R.id.chat_msgs);
		mChatView.setAdapter(mChatArrayAdapter);

		// Initialize the compose field with a listener for the return key
		mOutMsgText = (EditText) findViewById(R.id.msg_out);
		mOutMsgText
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					public boolean onEditorAction(TextView view, int actionId,
							KeyEvent event) {
						// If the action is a key-up event on the return key,
						// send the message
						if (actionId == EditorInfo.IME_NULL
								&& event.getAction() == KeyEvent.ACTION_UP) {
							String message = view.getText().toString();
							view.setText("");
							sendMsg(message);
						}
						return true;
					}
				});

		// Initialize the send button with a listener that for click events
		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String message = mOutMsgText.getText().toString();
				sendMsg(message);
				mOutMsgText.setText("");
			}
		});
		mSendButton.setEnabled(false);

		// bind to router messenger
		doBindService();
	}

	@Override
	public void onDestroy() {
		doUnbindService();
		super.onDestroy();
	}

	private void sendMsg(String msg_data) {
		// show my msg first
		mChatArrayAdapter.add("Me: " + msg_data);
		// send my msg
		if (mService != null) {
			Message msg = Message.obtain(null, Router.MsgId.SEND_MSG, 0, 0);
			Bundle b = new Bundle();
			b.putByteArray(Router.MSG_DATA, msg_data.getBytes());
			b.putString(Router.GROUP_ID, groupId);
			msg.setData(b);
			try {
				mService.send(msg);
			} catch (RemoteException e) {
				// Log.e(TAG, "failed to connect peer: " + e.getMessage());
			}
		}
	}

}
