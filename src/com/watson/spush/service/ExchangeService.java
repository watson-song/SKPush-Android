package com.watson.spush.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.watson.spush.MainActivity;
import com.watson.spush.R;
import com.watson.spush.service.util.ExchangeClient;
import com.watson.spush.service.util.ExchangeClient.OnExchangeCallback;

public class ExchangeService extends Service {
	/*** Level for heartbeat, 0 stop the heartbeat, shutdown the exchangeservice, 1 for idle, 2 for busy*/
	int mRunningLevel = 0;
	String mHostName;
	int mPort = 0;
	ExchangeClient mExchangeClient;
	Thread mExchangeClientThread;

	/*** For showing and hiding our notification. */
    NotificationManager mNM;
    /*** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    
    /***
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 1;
    
    /***
     * Command to the service to unregister a client, ot stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 2;

    /***
     * Command to update the exchange client's statues, include hostname, port and connect state
     */
    public static final int MSG_UPDATE_EXCHANGE_STATUE = 3;
    
    public static final int HEARTBEATING = 9;
    public static final int EXCHANGE_CONNECT_FAILED = 10;
    public static final int EXCHANGE_CONNECT_SUCCESS = 11;
    public static final int MSG_PUSH_TO_SERVER = 12;
    public static final int MSG_PUSH_FROM_SERVER = 13;
    public static final int EXCHANGE_CONNECTION_CLOSED = 14;
    public static final int MSG_BIND_TAGS = 15;
    public static final int MSG_UNBIND_TAG = 16;
    public static final int MSG_API_CALL = 17;
    
    /***
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    if(mExchangeClient!=null) {
                    	try {
                    		HashMap<String, Object> connectionState = new HashMap<String, Object>();
                    		connectionState.put("host_name", mExchangeClient.getHostName());
                    		connectionState.put("port", mExchangeClient.getPort());
                    		connectionState.put("state", mExchangeClient.getState());
                    		msg.replyTo.send(Message.obtain(null, MSG_UPDATE_EXCHANGE_STATUE, connectionState));
                    	} catch (RemoteException e) {
                    		// TODO Auto-generated catch block
                    		e.printStackTrace();
                    	}
                    }
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_BIND_TAGS:
                case MSG_UNBIND_TAG:
                case MSG_API_CALL:
                case MSG_PUSH_TO_SERVER:
                	if(mExchangeClient!=null&&mExchangeClient.getState()==2) {
                		mExchangeClient.send(msg.arg1, (byte)msg.arg2, (String)msg.obj);
                	}
                	break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    
    /***
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.
        showNotification(REMOTE_SERVICE_STARTED_NOTIFICATION, getText(R.string.remote_service_started), "后台服务已经启动");
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(R.string.remote_service_started);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();
    }
    
    /***
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    public static final int REMOTE_SERVICE_STARTED_NOTIFICATION = 1;
    public static final int NEW_MESSAGE_NOTIFICATION = 2;
    /***
     * Show a notification while this service is running.
     */
    @SuppressLint("NewApi")
    private void showNotification(int id, CharSequence charSequence, String msg) {
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        
        // Set the icon, scrolling text and timestamp
        Notification.Builder builder = new Notification.Builder(this).setContentTitle(charSequence).setSmallIcon(R.drawable.ic_launcher).setContentIntent(contentIntent);
        switch(id) {
        case REMOTE_SERVICE_STARTED_NOTIFICATION:
        	builder.setTicker(charSequence).setContentText(msg);
        	break;
        case NEW_MESSAGE_NOTIFICATION:
        	try {
    			JSONObject json = new JSONObject(msg);
    			String from = "Server";
    			String content = msg;
    			if(json.has("from")) {
    				from = json.getString("from");
    			}
    			if(json.has("data")) {
    				content = json.getString("data");
    			}
    			builder.setContentText(from+": "+content).setTicker(charSequence+"\n"+from+": "+content);
    		} catch (JSONException e) {
    			e.printStackTrace();
    			builder.setContentText(msg).setTicker(charSequence+"\n"+msg);
    		}
        	builder.setAutoCancel(true).setShowWhen(true).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).setWhen(System.currentTimeMillis());
        	break;
        }
        Notification notification = builder.build();

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(id, notification);
    }
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent!=null) {
			int runningLevel = intent.getIntExtra("running_level", -1);
			String hostName = intent.getStringExtra("host_name");
			int port = intent.getIntExtra("port", 8080);
			if((hostName!=null)&&((!hostName.equalsIgnoreCase(mHostName)||port!=mPort))||(mExchangeClient!=null&&mExchangeClient.getState()==3)) {
				if(mExchangeClient!=null&&mExchangeClient.getState()==2) {
					mExchangeClient.close();
				}
				
				mRunningLevel = runningLevel;
				mHostName = hostName;
				mPort = port;
				
				//start a new client
				mExchangeClient = new ExchangeClient(mHostName, mPort, new OnExchangeCallback() {
					static final String TAG = "ExchangeService-OnExchangeCallback";
					
					@Override
					public void onMessageReceived(byte[] message) {
						if(mClients.size()>0) {
							for (int i=mClients.size()-1; i>=0; i--) {
								try {
									mClients.get(i).send(Message.obtain(null, MSG_PUSH_FROM_SERVER, message));
								} catch (RemoteException e) {
									// The client is dead.  Remove it from the list;
									// we are going through the list from back to front
									// so this is safe to do inside the loop.
									mClients.remove(i);
								}
							}
						}else {
							showNotification(NEW_MESSAGE_NOTIFICATION, getText(R.string.notification_new_message), new String(message));
						}
					}

					@Override
					public void onServerConnectSuccess() {
						Log.e(TAG, "onServerConnectSuccess");
						for (int i=mClients.size()-1; i>=0; i--) {
	                        try {
	                            mClients.get(i).send(Message.obtain(null, EXCHANGE_CONNECT_SUCCESS));
	                        } catch (RemoteException e) {
	                            // The client is dead.  Remove it from the list;
	                            // we are going through the list from back to front
	                            // so this is safe to do inside the loop.
	                            mClients.remove(i);
	                        }
	                    }
					}

					@Override
					public void onServerConnectFailed(IOException ex) {
						Log.e(TAG, "onServerConnectFailed => "+ex);
						for (int i=mClients.size()-1; i>=0; i--) {
	                        try {
	                            mClients.get(i).send(Message.obtain(null, EXCHANGE_CONNECT_FAILED, ex.getMessage()));
	                        } catch (RemoteException e) {
	                            // The client is dead.  Remove it from the list;
	                            // we are going through the list from back to front
	                            // so this is safe to do inside the loop.
	                            mClients.remove(i);
	                        }
	                    }
					}

					@Override
					public void heartBeating() {
						for (int i=mClients.size()-1; i>=0; i--) {
	                        try {
	                            mClients.get(i).send(Message.obtain(null, HEARTBEATING));
	                        } catch (RemoteException e) {
	                            // The client is dead.  Remove it from the list;
	                            // we are going through the list from back to front
	                            // so this is safe to do inside the loop.
	                            mClients.remove(i);
	                        }
	                    }
					}

					@Override
					public void onServerConnectionClosed() {
						Log.e(TAG, "onServerConnectSuccess");
						for (int i=mClients.size()-1; i>=0; i--) {
	                        try {
	                            mClients.get(i).send(Message.obtain(null, EXCHANGE_CONNECTION_CLOSED));
	                        } catch (RemoteException e) {
	                            // The client is dead.  Remove it from the list;
	                            // we are going through the list from back to front
	                            // so this is safe to do inside the loop.
	                            mClients.remove(i);
	                        }
	                    }
					}

					@Override
					public void onBindDone(byte[] data) {
						Log.e(TAG, "onBindDone response -> "+new String(data));
					}

					@Override
					public void onApiCallback(byte[] data) {
						Log.e(TAG, "onApiCallback response -> "+new String(data));
					}
				});
				mExchangeClientThread = new Thread(){
					public void run() {
						mExchangeClient.start();
					};
				};
				mExchangeClientThread.start();
			}else if(runningLevel!=-1&&runningLevel!=mRunningLevel) {
				mRunningLevel = runningLevel;
				switch(mRunningLevel) {
				case 0:
					stopSelf();
					break;
				case 1:
				case 2:
					if(mExchangeClient!=null) {
						mExchangeClient.setRunningLevel(mRunningLevel);
					}
					break;
				}
			}
		}
		return START_STICKY;
	}

}
