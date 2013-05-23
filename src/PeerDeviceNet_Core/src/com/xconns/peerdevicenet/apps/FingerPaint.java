/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.xconns.peerdevicenet.apps;

import java.util.HashMap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.EmbossMaskFilter;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.IRouterGroupHandler;
import com.xconns.peerdevicenet.IRouterGroupService;
import com.xconns.peerdevicenet.Router;

//a simple msg for paint info. 
class PaintMsg {
	// msg ids
	public final static int TOUCH_DOWN = 1; //
	public final static int TOUCH_MOVE = 2; //
	public final static int TOUCH_UP = 3; //
	public final static int COLOR = 4; //
	public final static int EMBOSS = 5; //
	public final static int BLUR = 6; //
	public final static int ERASE = 7; //
	public final static int SRC_A_TOP = 8; //
		
	public int msgId; 
	public float x, y; //position
	public int val;
	 
	public PaintMsg() {
	}
	public PaintMsg(int id) {
		msgId = id;
	}
	
	//the following using android Parcel to marshaling data, could use JSON etc.
	public byte[] marshall() {
		final Parcel parcel = Parcel.obtain();
		byte[] data = null;
		parcel.writeInt(msgId);
		parcel.writeFloat(x);
		parcel.writeFloat(y);
		parcel.writeInt(val);
		data = parcel.marshall();
		parcel.recycle();
		return data;
	}

	public void unmarshall(byte[] data, int len) {
		final Parcel parcel = Parcel.obtain();
		parcel.unmarshall(data, 0, len);
		parcel.setDataPosition(0);
		msgId = parcel.readInt();
		x = parcel.readFloat();
		y = parcel.readFloat();
		val = parcel.readInt();
		parcel.recycle();
	}
}

class Painter {
    public final static MaskFilter  mEmboss = new EmbossMaskFilter(new float[] { 1, 1, 1 },
            0.4f, 6, 3.5f);
    public final static MaskFilter  mBlur = new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL);
    private static final float TOUCH_TOLERANCE = 4;

	public Paint mPaint;
	public Path mPath;
	
	float mX, mY; //cur pos
	
	public Painter() {
        mPath = new Path();
        mPaint = new Paint();
        reset();
	}
	
	public void reset() {
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(0xFFFF0000);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(12);

        mPath.reset();
        mX = -1;
        mY = -1;		
	}
	
	public void resetPath() {
		mPath.reset();
		mX = -1;
		mY = -1;
	}
	
	public boolean hasPath() {
		return mX != -1 || mY != -1;
	}
	
    public void touch_start(float x, float y) {
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }
    public void touch_move(float x, float y) {
		if (mX == -1 && mY == -1) {
			mPath.reset();
			mPath.moveTo(x, y);
			mX = x;
			mY = y;
		} else {
			float dx = Math.abs(x - mX);
			float dy = Math.abs(y - mY);
			if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
				mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
				mX = x;
				mY = y;
			}
		}
    }
    public void touch_up() {
        mPath.lineTo(mX, mY);
        /*
        // commit the path to our offscreen
        mCanvas.drawPath(mPath, mPaint);
        // kill this so we don't double draw
        mPath.reset();
        */
    }
}

public class FingerPaint extends Activity
        implements ColorPickerDialog.OnColorChangedListener {

	private static final String TAG = "FingerPaint";

	private static final String groupId = "PaintWith123!@#Peers";
	private IRouterGroupService mGroupService = null;
	
	MyView myView = null;
	Painter myPainter = new Painter();
	HashMap<String, Painter> peerPainters = new HashMap<String, Painter>();
	
	long startTime = System.currentTimeMillis();
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myView = new MyView(this);
        setContentView(myView);
     }
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		//bind to group service
		Intent intent = new Intent("com.xconns.peerdevicenet.GroupService");
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);       
	}	
	
	@Override
	protected void onPause() {
		if (mGroupService != null) {
			try {
				// leave group
				mGroupService.leaveGroup(groupId, mGroupHandler);
			} catch (RemoteException e) {
				// Log.e(TAG, "failed at leaveGroup: " + e.getMessage());
			}
		}
		// unbind service
		unbindService(mConnection);
		// TODO Auto-generated method stub
		super.onPause();
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mGroupService = IRouterGroupService.Stub.asInterface(service);
			Log.d(TAG, "GroupService connected");
			// join group
			try {
				mGroupService.joinGroup(groupId, null, mGroupHandler);
			} catch (RemoteException e) {
				Log.e(TAG, "failed at joinGroup: " + e.getMessage());
			}
			Log.d(TAG, "joined group: " + groupId);
		}

		public void onServiceDisconnected(ComponentName className) {
			mGroupService = null;
		}
	};
	
	private IRouterGroupHandler mGroupHandler = new IRouterGroupHandler.Stub() {

		public void onError(String errInfo) throws RemoteException {
			Log.d(TAG, "group comm error : " + errInfo);
		}

		public void onSelfJoin(DeviceInfo[] devices) throws RemoteException {
			if (devices != null && devices.length > 0) {
				//i have peers, sync my inital orientation with them
				/*
				PaintMsg m = new PaintMsg(PaintMsg.INIT_BITMAP_REQ); //req init orientation
				
				mGroupService.send(groupId, null, m.marshall());
				*/
				Message msg = mHandler.obtainMessage(Router.MsgId.SELF_JOIN);
				msg.obj = devices;
				mHandler.sendMessage(msg);
			}
		}

		public void onPeerJoin(DeviceInfo device) throws RemoteException {
			Log.d(TAG, "peer join: "+device.toString());
			Message msg = mHandler.obtainMessage(Router.MsgId.PEER_JOIN);
			msg.obj = device;
			mHandler.sendMessage(msg);
		}

		public void onSelfLeave() throws RemoteException {
		}

		public void onPeerLeave(DeviceInfo device) throws RemoteException {
			Log.d(TAG, "peer leave: "+device.toString());
			Message msg = mHandler.obtainMessage(Router.MsgId.PEER_LEAVE);
			msg.obj = device;
			mHandler.sendMessage(msg);
		}

		public void onReceive(DeviceInfo src, byte[] b) throws RemoteException {
			Log.d(TAG, "recv paint info from "+src.toString());
			Message msg = mHandler.obtainMessage(Router.MsgId.RECV_MSG);
			msg.obj = new Object[]{src, b};
			mHandler.sendMessage(msg);
		}

		public void onGetPeerDevices(DeviceInfo[] devices)
				throws RemoteException {
		}
	};

	/**
	 * Handler of incoming messages from service.
	 */
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Router.MsgId.SELF_JOIN:
				DeviceInfo[] devices = (DeviceInfo[]) msg.obj;
				if (devices != null) {
					for (DeviceInfo dev : devices) {
						if (dev.addr != null) {
							peerPainters.put(dev.addr, new Painter());
							myView.reset();
							myPainter.reset();
							myView.invalidate();
						}
					}
				}
				break;
			case Router.MsgId.PEER_JOIN:
				DeviceInfo dev = (DeviceInfo) msg.obj;
				if (dev!=null && dev.addr!=null) {
					peerPainters.put(dev.addr, new Painter());
					myView.reset();
					myPainter.reset();
					myView.invalidate();
				}
				break;
			case Router.MsgId.PEER_LEAVE:
				dev = (DeviceInfo) msg.obj;
				if (dev!=null && dev.addr!=null) {
					peerPainters.remove(dev.addr);
					myView.invalidate();
				}
				break;
			case Router.MsgId.RECV_MSG:
				Object[] data = (Object[]) msg.obj;
				dev = (DeviceInfo) data[0];
				byte[] rawbytes = (byte[]) data[1];
				PaintMsg m = new PaintMsg();
				m.unmarshall(rawbytes, rawbytes.length);
				procPaintMsgFromPeer(dev, m);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};
	
	void procPaintMsgFromPeer(DeviceInfo dev, PaintMsg m) {
		if (dev == null || dev.addr == null || m == null) return;
		Painter p = peerPainters.get(dev.addr);
		if (p == null) return;
		float x = m.x*myView.ww;
		float y = m.y*myView.hh;
		switch (m.msgId) {
		case PaintMsg.TOUCH_DOWN:
			p.touch_start(x, y);
			myView.invalidate();
			break;
		case PaintMsg.TOUCH_MOVE:
			p.touch_move(x, y);
			myView.invalidate();
			break;
		case PaintMsg.TOUCH_UP:
			p.touch_up();
            // commit the path to our offscreen
            myView.mCanvas.drawPath(p.mPath, p.mPaint);
            // kill this so we don't double draw
            p.resetPath();
			myView.invalidate();

			break;
		case PaintMsg.EMBOSS:
	        p.mPaint.setXfermode(null);
	        p.mPaint.setAlpha(0xFF);

			Log.d(TAG, "recv msg Emboss");
            if (p.mPaint.getMaskFilter() != Painter.mEmboss) {
            	p.mPaint.setMaskFilter(Painter.mEmboss);
            } else {
            	p.mPaint.setMaskFilter(null);
            }
			break;
		case PaintMsg.COLOR:
	        p.mPaint.setXfermode(null);
	        p.mPaint.setAlpha(0xFF);

			Log.d(TAG, "recv msg Color");
	    	p.mPaint.setColor(m.val);
			break;
		case PaintMsg.BLUR:
	        p.mPaint.setXfermode(null);
	        p.mPaint.setAlpha(0xFF);

			Log.d(TAG, "recv msg Blur");
           if (p.mPaint.getMaskFilter() != Painter.mBlur) {
            	p.mPaint.setMaskFilter(Painter.mBlur);
            } else {
            	p.mPaint.setMaskFilter(null);
            }
			break;
		case PaintMsg.ERASE:
	        p.mPaint.setXfermode(null);
	        p.mPaint.setAlpha(0xFF);

			Log.d(TAG, "recv msg Erase");
        	p.mPaint.setXfermode(new PorterDuffXfermode(
                    PorterDuff.Mode.CLEAR));
			break;
		case PaintMsg.SRC_A_TOP:
	        p.mPaint.setXfermode(null);
	        p.mPaint.setAlpha(0xFF);

        	p.mPaint.setXfermode(new PorterDuffXfermode(
                    PorterDuff.Mode.SRC_ATOP));
        	p.mPaint.setAlpha(0x80);
			break;
		default:
			break;
		}
	}

	
	//send rotate info to peers
	public void sendPaintMsgToPeers(PaintMsg m) {
		if (mGroupService != null) {
			try {
				Log.d(TAG, "send Paint msg");
				mGroupService.send(groupId, null, m.marshall());
			} 
			catch (RemoteException re) {
				Log.d(TAG, "fail to send Paint msg");
			}
		}
	}
	
    public void colorChanged(int color) {
    	myPainter.mPaint.setColor(color);
    	PaintMsg pm = new PaintMsg(PaintMsg.COLOR);
    	pm.val = color;
    	sendPaintMsgToPeers(pm);
    }

    public class MyView extends View {

        private static final float MINP = 0.25f;
        private static final float MAXP = 0.75f;

        private Bitmap  mBitmap;
        private Canvas  mCanvas;
        private Paint   mBitmapPaint;
        
        FingerPaint context = null;
        int ww, hh;

        public MyView(Context c) {
            super(c);
            context = (FingerPaint)c;

            mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            ww = w; 
            hh = h;
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }
        
        public void reset() {
        	mBitmap = Bitmap.createBitmap(ww, hh, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFF505050);
            //draw past paths already saved
            canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
            //draw my current path
            canvas.drawPath(myPainter.mPath, myPainter.mPaint);
            //draw peers current paths
            for(Painter p : context.peerPainters.values()) {
            	if (p.hasPath()) {
            		canvas.drawPath(p.mPath, p.mPaint);
            	}
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            PaintMsg pm = null;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    myPainter.touch_start(x, y);
                    invalidate();
                    pm = new PaintMsg(PaintMsg.TOUCH_DOWN);
                    pm.x = x/ww;
                    pm.y = y/hh;
                    sendPaintMsgToPeers(pm);
                    break;
                case MotionEvent.ACTION_MOVE:
                	myPainter.touch_move(x, y);
                	// commit the path to our offscreen
                    //mCanvas.drawPath(myPainter.mPath, myPainter.mPaint);
                    invalidate();
                    pm = new PaintMsg(PaintMsg.TOUCH_MOVE);
                    pm.x = x/ww;
                    pm.y = y/hh;
                    sendPaintMsgToPeers(pm);
                    break;
                case MotionEvent.ACTION_UP:
                	myPainter.touch_up();
                    // commit the path to our offscreen
                    mCanvas.drawPath(myPainter.mPath, myPainter.mPaint);
                    // kill this so we don't double draw
                    myPainter.resetPath();
                    invalidate();
                    pm = new PaintMsg(PaintMsg.TOUCH_UP);
                    pm.x = x/ww;
                    pm.y = y/hh;
                    sendPaintMsgToPeers(pm);
                    break;
            }
            return true;
        }
    }

    private static final int COLOR_MENU_ID = Menu.FIRST;
    private static final int EMBOSS_MENU_ID = Menu.FIRST + 1;
    private static final int BLUR_MENU_ID = Menu.FIRST + 2;
    private static final int ERASE_MENU_ID = Menu.FIRST + 3;
    private static final int SRCATOP_MENU_ID = Menu.FIRST + 4;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, COLOR_MENU_ID, 0, "Color").setShortcut('3', 'c');
        menu.add(0, EMBOSS_MENU_ID, 0, "Emboss").setShortcut('4', 's');
        menu.add(0, BLUR_MENU_ID, 0, "Blur").setShortcut('5', 'z');
        menu.add(0, ERASE_MENU_ID, 0, "Erase").setShortcut('5', 'z');
        menu.add(0, SRCATOP_MENU_ID, 0, "SrcATop").setShortcut('5', 'z');

        /****   Is this the mechanism to extend with filter effects?
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(
                              Menu.ALTERNATIVE, 0,
                              new ComponentName(this, NotesList.class),
                              null, intent, 0, null);
        *****/
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        myPainter.mPaint.setXfermode(null);
        myPainter.mPaint.setAlpha(0xFF);

        switch (item.getItemId()) {
            case COLOR_MENU_ID:
                new ColorPickerDialog(this, this, myPainter.mPaint.getColor()).show();
                return true;
            case EMBOSS_MENU_ID:
                if (myPainter.mPaint.getMaskFilter() != Painter.mEmboss) {
                	myPainter.mPaint.setMaskFilter(Painter.mEmboss);
                } else {
                	myPainter.mPaint.setMaskFilter(null);
                }
                PaintMsg pm = new PaintMsg(PaintMsg.EMBOSS);
                sendPaintMsgToPeers(pm);
                return true;
            case BLUR_MENU_ID:
                if (myPainter.mPaint.getMaskFilter() != Painter.mBlur) {
                	myPainter.mPaint.setMaskFilter(Painter.mBlur);
                } else {
                	myPainter.mPaint.setMaskFilter(null);
                }
                pm = new PaintMsg(PaintMsg.BLUR);
                sendPaintMsgToPeers(pm);
                return true;
            case ERASE_MENU_ID:
            	myPainter.mPaint.setXfermode(new PorterDuffXfermode(
                                                        PorterDuff.Mode.CLEAR));
                pm = new PaintMsg(PaintMsg.ERASE);
                sendPaintMsgToPeers(pm);
                 return true;
            case SRCATOP_MENU_ID:
            	myPainter.mPaint.setXfermode(new PorterDuffXfermode(
                                                    PorterDuff.Mode.SRC_ATOP));
            	myPainter.mPaint.setAlpha(0x80);
                pm = new PaintMsg(PaintMsg.SRC_A_TOP);
                pm.val = 1;
                sendPaintMsgToPeers(pm);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
