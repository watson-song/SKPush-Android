package com.watson.spush.service.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;

import android.util.Log;

public class ExchangeClient {
	public static final String TAG = "ExchangeClient";
	
	private String mHostName;
	private int mPort;
	private int mRunningLevel = 2;//1 for idle, 2 for busy
	private int mConnectRetryCount = 0;
	private Socket mSocket;
	private BufferedOutputStream mOutPutStream;
	private PrintWriter mPrintWriter;
	private BufferedReader mBufferedReader;
	
	/*** State for the connection, 0 for unconnected, 1 for connecting, 2 for connected, 3 for closed*/
	private int mState;
	
	private OnExchangeCallback mExchangeCallback;
	public interface OnExchangeCallback {
		void onServerConnectSuccess();
		void onServerConnectFailed(IOException ex);
		void heartBeating();
		void onMessageReceived(byte[] data);
		void onBindDone(byte[] data);
		void onApiCallback(byte[] data);
		void onServerConnectionClosed();
	}

	public ExchangeClient(String hostName, int port, OnExchangeCallback exchangeCallback) {
		this.mHostName = hostName;
		this.mPort = port;
		this.mExchangeCallback = exchangeCallback;
	}
	
	public void start() {
		try{
			mState = 1;
			mConnectRetryCount = 0;
			mSocket = new Socket(mHostName, mPort);
			mSocket.setKeepAlive(true);
			mOutPutStream = new BufferedOutputStream(mSocket.getOutputStream());
			mPrintWriter = new PrintWriter(mOutPutStream);
			mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
			mState = 2;
			
			if(mExchangeCallback!=null) {
				mExchangeCallback.onServerConnectSuccess();
			}
			
			//bind id
			send(Constants.SKEP_COMMAND_REQUEST_BIND_ID, (byte)1, "\""+System.currentTimeMillis()+"\"");
			
			new Thread(){
				public void run() {
					while(mState==2) {
						try {
							sleep(mRunningLevel==1?20000:5000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						send(mRunningLevel==2?Constants.SKEP_COMMAND_REQUEST_HEARTBEAT_BUSY:Constants.SKEP_COMMAND_REQUEST_HEARTBEAT_IDLE, (byte)1, null);//Constants.SKEP_COMMAND_REQUEST_HEARTBEAT_IDLE/BUSY
					}
				};
			}.start();
			
			String readLine = "";
			while(mConnectRetryCount<5){
				readLine = mBufferedReader.readLine();
				if(readLine==null) {
					mConnectRetryCount++;
				}else{
					mConnectRetryCount = 0;
					if(mExchangeCallback!=null) {
						byte[] message = getMessage(readLine);
						if(message!=null) {
							byte version = message[0];
							byte packageType = message[1];
							int command = byteArrayToInt(Arrays.copyOfRange(message, 2, 6));
							int dataLength = byteArrayToInt(Arrays.copyOfRange(message, 6, 10));
							byte[] data = message.length>10?Arrays.copyOfRange(message, 10, message.length):new byte[0];
							switch(command) {
							case Constants.SKEP_COMMAND_MSG_PUSH_IN:
								mExchangeCallback.onMessageReceived(data);
								break;
							case Constants.SKEP_COMMAND_RESPONSE_API_CALL:
								mExchangeCallback.onApiCallback(data);
								break;
							case Constants.SKEP_COMMAND_RESPONSE_BIND_TAGS:
							case Constants.SKEP_COMMAND_RESPONSE_UNBIND_TAG:
							case Constants.SKEP_COMMAND_RESPONSE_BIND_ID:
								mExchangeCallback.onBindDone(data);
								break;
							case Constants.SKEP_COMMAND_RESPONSE_HEARTBEAT:
								mExchangeCallback.heartBeating();
								break;
								default:
									Log.e(TAG, "Unknow command "+command+"(version="+version+",packageType="+packageType+")");
								break;
							}
						}
					}
				}
				Log.i(TAG, "Server msg:" + (readLine!=null?contactByteArray(readLine.getBytes()):null));
			}
			
		}catch(IOException e){
			Log.e(TAG, "START ->" + e);
			
			if(mExchangeCallback!=null) {
				mExchangeCallback.onServerConnectFailed(e);
			}
		}finally {
			close();
		}
	}
	
	private String contactByteArray(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for(byte b:data) {
			sb.append(b+",");
		}
		return sb.toString();
	}
	
	/***
	 * Send msg to server, version 1, packageType is normal
	 */
	public void send(int command, String msg) {
		send(command, (byte)1, (byte)0, msg);
	}
	
	/***
	 * Send msg to server, version 1
	 */
	public void send(int command, byte packageType, String msg) {
		send(command, (byte)1, packageType, msg);
	}
	
	/***
	 * Send msg to server, msg protocol format:
	 * HEAD:%%(2bytes)
	 * VERSION:1(1bytes)
	 * PACKAGETYPE:1(1bytes) //0 NORMAL, 1 REQUEST, 2 RESPONSE
	 * COMMAND:integer(4bytes)
	 * LENGTH:integer(4bytes)  data length only
	 * JSONDATA:data(anybytes)
	 * END:$$(2bytes)
	 * @param msg -> json data
	 */
	public void send(int command, byte version, byte packageType, String msg) {
		if(mState==2&&mSocket.isConnected()) {
			
			try {
				mOutPutStream.write('%');//head1
				mOutPutStream.write('%');//head2
				mOutPutStream.write(version);//version  
				mOutPutStream.write(packageType);//version  
				mOutPutStream.write(intToByteArray(command));
				byte[] data = msg!=null?msg.getBytes("utf-8"):new byte[0];
				mOutPutStream.write(intToByteArray(data.length));
				mOutPutStream.write(data);//json data
				mOutPutStream.write('$');//end1
				mOutPutStream.write('$');//end2
				mOutPutStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.i(TAG, "Send msg:" + msg);
		}
	}
	
	private byte[] getMessage(String data) {
		byte[] byteArray = data.getBytes();
		int startIndex = -1, endIndex = -1;
		for(int i=0;i<byteArray.length-1;i++) {
			if(byteArray[i]==37&&byteArray[i+1]==37) {
				startIndex = i+2;
				break;
			}
			if(byteArray[i]==36&&byteArray[i+1]==36) {
				endIndex = i;
				break;
			}
		}
		
		if(startIndex!=-1) {
			for(int i=0;i<byteArray.length-1;i++) {
				if(byteArray[i]==36&&byteArray[i+1]==36) {
					endIndex = i;
					break;
				}
			}
			if(endIndex!=-1) {
				return Arrays.copyOfRange(byteArray, startIndex, endIndex);
			}
		}
		
		//not found correct message
		return null;
	}
	
	public static final byte[] intToByteArray(int value) {
	    return new byte[] {(byte)(value >>> 24),(byte)(value >>> 16),(byte)(value >>> 8),(byte)value};
	}
	
	public static int byteArrayToInt(byte[] b) {
		return b[3] & 0xFF | (b[2] & 0xFF) << 8 | (b[1] & 0xFF) << 16 |(b[0] & 0xFF) << 24;
	}
	
	public void close() {
		if(mState==2&&mSocket.isConnected()) {
			try {
				mPrintWriter.close();
				mBufferedReader.close();
				mSocket.close();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(mExchangeCallback!=null) {
			mExchangeCallback.onServerConnectionClosed();
		}
		mPrintWriter = null;
		mBufferedReader = null;
		mSocket = null;
		mState = 3;
		Log.i(TAG, "Close connection for exchange client");
	}

	public void setRunningLevel(int runningLevel) {
		this.mRunningLevel = runningLevel;
	}

	public int getState() {
		return mState;
	}

	public Object getHostName() {
		return mHostName;
	}
	
	public int getPort() {
		return mPort;
	}
}
