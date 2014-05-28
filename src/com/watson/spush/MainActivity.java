package com.watson.spush;

import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.watson.spush.service.ExchangeService;
import com.watson.spush.service.util.Constants;
import com.watson.spush.util.SystemUiHider;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class MainActivity extends Activity {
	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = false;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;
	
	// BEGIN_INCLUDE(bind)
    /*** Messenger for communicating with service. */
    Messenger mService = null;
    /*** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;
    /*** Some text view we are using to show state information. */
    TextView mServiceCallbackText, mConnectionCallbackText, mHeartBeatingText, mHostNameText, mPortText;
   
    LinearLayout mMessageContainer;
    EditText mSendContentEditText;
    Button connectBtn, sendBtn;
    
    /***
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
	            case ExchangeService.MSG_UPDATE_EXCHANGE_STATUE:
	            	HashMap<String, Object> connectionState = (HashMap<String, Object>)msg.obj;
	            	if(connectionState!=null) {
	            		Integer state = (Integer)connectionState.get("state");
	            		if(state==2) {
	            			mHostNameText.setText((String)connectionState.get("host_name"));
	            			mPortText.setText(String.valueOf((Integer)connectionState.get("port")));
	            			doExchangeConnectSuccess();
	            		}
	            	}
	            	break;
                case ExchangeService.EXCHANGE_CONNECT_SUCCESS:
                	doExchangeConnectSuccess();
                	break;
                case ExchangeService.EXCHANGE_CONNECT_FAILED:
                	doExchangeConnectionException((String)msg.obj);
                	break;
                case ExchangeService.MSG_PUSH_FROM_SERVER:
                	createComeMessageItem(new String((byte[])msg.obj));
                	break;
                case ExchangeService.HEARTBEATING:
                	doHeartBeating();
                	break;
                case ExchangeService.EXCHANGE_CONNECTION_CLOSED:
                	doExchangeConnectionDone();
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
    
    /***
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
    	
    	@Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);
            mServiceCallbackText.setText("Attached.");

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null, ExchangeService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
            
            // As part of the sample, tell the user what happened.
            Toast.makeText(MainActivity.this, R.string.remote_service_connected, Toast.LENGTH_SHORT).show();
        }

    	@Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mServiceCallbackText.setText("Disconnected.");

            // As part of the sample, tell the user what happened.
            Toast.makeText(MainActivity.this, R.string.remote_service_disconnected, Toast.LENGTH_SHORT).show();
        }
    };
    
    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
    	Intent esIntent = new Intent(MainActivity.this, ExchangeService.class);
        bindService(esIntent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        mServiceCallbackText.setText("Binding.");
    }
    
    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, ExchangeService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }
            
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            mServiceCallbackText.setText("Unbinding.");
        }
    }
    // END_INCLUDE(bind)

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		mServiceCallbackText = (TextView)findViewById(R.id.bind_state_textView);
		mConnectionCallbackText = (TextView)findViewById(R.id.connection_state_textView);
		mHeartBeatingText = (TextView)findViewById(R.id.heartbeating_textView);
		mHostNameText = (TextView)findViewById(R.id.editText_hostname);
		mHostNameText.setText("172.30.86.29");
		mPortText = (TextView)findViewById(R.id.editText_port);
		mSendContentEditText = (EditText)findViewById(R.id.editText_send_content);
		mSendContentEditText.setOnEditorActionListener(new OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if(event.getKeyCode()==KeyEvent.KEYCODE_ENTER) {
					doSendMessage(v.getText().toString());
					return true;
				}
				return false;
			}
		});
		mMessageContainer = (LinearLayout) findViewById(R.id.message_container);
		connectBtn = (Button)findViewById(R.id.button_connect);
		connectBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent esIntent = new Intent(MainActivity.this, ExchangeService.class);
		        esIntent.putExtra("host_name", mHostNameText.getText().toString());
		        esIntent.putExtra("port", Integer.parseInt(mPortText.getText().toString()));
		        startService(esIntent);
			}
		});
		sendBtn = (Button)findViewById(R.id.button_send);
		sendBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doSendMessage(mSendContentEditText.getText().toString());
			}
		});

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
			// Cached values.
			int mControlsWidth;
			int mShortAnimTime;

			@Override
			@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
			public void onVisibilityChange(boolean visible) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
					// If the ViewPropertyAnimator API is available
					// (Honeycomb MR2 and later), use it to animate the
					// in-layout UI controls at the bottom of the
					// screen.
					if (mControlsWidth == 0) {
						mControlsWidth = controlsView.getWidth();
					}
					if (mShortAnimTime == 0) {
						mShortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
					}
					controlsView.animate().translationX(visible ? 0 : mControlsWidth).setDuration(mShortAnimTime);
				} else {
					// If the ViewPropertyAnimator APIs aren't
					// available, simply show or hide the in-layout UI
					// controls.
					controlsView.setVisibility(visible ? View.VISIBLE: View.GONE);
				}

//				if (visible && AUTO_HIDE) {
//					// Schedule a hide().
//					delayedHide(AUTO_HIDE_DELAY_MILLIS);
//				}
			}
		});

		// Set up the user interaction to manually show or hide the system UI.
		findViewById(R.id.fullscreen_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
//		findViewById(R.id.fullscreen_button).setOnTouchListener(mDelayHideTouchListener);
		
		doBindService();
	}
	
	private void doSendMessage(String data) {
		if(mService!=null) {
			try {
				Message msg = Message.obtain(null, ExchangeService.MSG_PUSH_TO_SERVER, Constants.SKEP_COMMAND_MSG_PUSH_OUT, 0, buildPushOutCommand(data, new String[]{"//"}));
				msg.replyTo = mMessenger;
				mService.send(msg);
				createGoMessageItem(data);
			} catch (RemoteException e) {
				e.printStackTrace();
				Toast.makeText(this, "Message Send failed -> service not binded.", Toast.LENGTH_LONG).show();
			}
		}
	}
	
	private void doBindTags(String data) {
		if(mService!=null) {
			try {
				Message msg = Message.obtain(null, ExchangeService.MSG_BIND_TAGS, Constants.SKEP_COMMAND_REQUEST_BIND_TAGS, 0, buildBindTagsCommand(data.split(",")));
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
				Toast.makeText(this, "Message Send failed -> service not binded.", Toast.LENGTH_LONG).show();
			}
		}
	}
	
	private void doUnBindTag(String tag) {
		if(mService!=null) {
			try {
				Message msg = Message.obtain(null, ExchangeService.MSG_UNBIND_TAG, Constants.SKEP_COMMAND_REQUEST_UNBIND_TAG, 0, "\""+tag+"\"");
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
				Toast.makeText(this, "Message Send failed -> service not binded.", Toast.LENGTH_LONG).show();
			}
		}
	}
	
	private String buildPushOutCommand(String content, String[] tags) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"data\":\""+content+"\",");
		sb.append("\"tags\":[");
		for(int i=0;i<tags.length;i++) {
			sb.append("\""+tags[i]+"\"");
			if(i<tags.length-1) {
				sb.append(",");
			}
		}
		sb.append("]");
		sb.append("}");
		return sb.toString();
	}
	
	private String buildBindTagsCommand(String[] tags) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for(int i=0;i<tags.length;i++) {
			sb.append("\""+tags[i]+"\"");
			if(i<tags.length-1) {
				sb.append(",");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	private void doExchangeConnectSuccess() {
		mHostNameText.setEnabled(false);
		mPortText.setEnabled(false);
		connectBtn.setText("Disconnect");
		sendBtn.setEnabled(true);
		mConnectionCallbackText.setText("Server Connected");
	}
	
	private void doExchangeConnectionDone() {
		mHostNameText.setEnabled(true);
		mPortText.setEnabled(true);
		connectBtn.setText("Connect");
		sendBtn.setEnabled(false);
		mConnectionCallbackText.setText("Connection Done");
	}
	
	private void doHeartBeating() {
		mHeartBeatingText.setTextColor(Color.GREEN);
		mHeartBeatingText.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				mHeartBeatingText.setTextColor(Color.TRANSPARENT);
			}
		}, 2000);
	}
	
	private void doExchangeConnectionException(String errorMsg) {
		mHostNameText.setEnabled(true);
		mPortText.setEnabled(true);
		connectBtn.setText("Connect");
		sendBtn.setEnabled(false);
		mConnectionCallbackText.setText("Connection Exception: "+errorMsg);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
        updateExchangeServiceLevel(1);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		updateExchangeServiceLevel(2);
	}
	
	private void updateExchangeServiceLevel(int level) {
		if(mIsBound) {
			Intent esIntent = new Intent(MainActivity.this, ExchangeService.class);
			esIntent.putExtra("running_level", level);
			startService(esIntent);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		doUnbindService();
	}

	@Override
	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return super.onCreateOptionsMenu(menu);
	};
	
	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		final EditText editText = new EditText(this);
		switch (item.getItemId()) {
		case R.id.bind_tags:
			new AlertDialog.Builder(this).setTitle("Please type the tags, seprate by ','").setIcon(android.R.drawable.ic_dialog_info).setView(editText).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					doBindTags(editText.getText().toString());
				}

			}).setNegativeButton("Cancle", null).show();
			break;
		case R.id.unbind_tag:
			new AlertDialog.Builder(this).setTitle("Please type the tag which you want to remove").setIcon(android.R.drawable.ic_dialog_info).setView(editText).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					doUnBindTag(editText.getText().toString());
				}
				
			}).setNegativeButton("Cancle", null).show();
			break;
		}
		return super.onOptionsItemSelected(item);
	};
	
	private void createComeMessageItem(String data) {
		if(data==null)return;
		
		TextView textView = new TextView(this);
		try {
			JSONObject json = new JSONObject(data);
			String from = "Server";
			String content = data;
			String time = "";
			if(json.has("from")) {
				from = json.getString("from");
			}
			if(json.has("data")) {
				content = json.getString("data");
			}
			if(json.has("timestamp")) {
				time = new Date(json.getLong("timestamp")).toLocaleString();
			}
			textView.setText(from+" -> "+content+"\n"+time);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			textView.setText("Server -> "+data);
		}
		textView.setTextColor(Color.WHITE);
		textView.setTextSize(16);
		textView.setPadding(0, 5, 0, 5);
		mMessageContainer.addView(textView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		mMessageContainer.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				((ScrollView)findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
			}
		}, 300);
	}
	
	private void createGoMessageItem(String data) {
		if(data==null)return;
		
		TextView textView = new TextView(this);
		textView.setText(data+" <- Me\n"+new Date().toLocaleString());
		textView.setTextColor(Color.GRAY);
		textView.setTextSize(16);
		textView.setPadding(0, 5, 0, 5);
		textView.setGravity(Gravity.RIGHT);
		mMessageContainer.addView(textView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		mSendContentEditText.setText("");
		mMessageContainer.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				((ScrollView)findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
			}
		}, 300);
	}
	
	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}
}
