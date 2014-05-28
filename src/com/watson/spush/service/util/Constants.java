package com.watson.spush.service.util;

public class Constants {
	
	/*** Scala Akka Exchange Protocol Commands - start*/
	
	/*** Normal command message 100-299*/
	public static final int SKEP_COMMAND_MSG_PUSH_OUT = 100; 
	public static final int SKEP_COMMAND_MSG_PUSH_IN = 101;
	public static final int SKEP_COMMAND_MSG_TRANSFER = 102;
	
	/*** Request command message 300-599*/
	public static final int SKEP_COMMAND_REQUEST_HEARTBEAT_IDLE = 300;
	public static final int SKEP_COMMAND_REQUEST_HEARTBEAT_BUSY = 301;
	public static final int SKEP_COMMAND_REQUEST_BIND_ID = 302;
	public static final int SKEP_COMMAND_REQUEST_BIND_TAGS = 303;
	public static final int SKEP_COMMAND_REQUEST_UNBIND_TAG = 304;
	public static final int SKEP_COMMAND_REQUEST_API_CALL = 305;
	
	/*** Response command message 600-899*/
	public static final int SKEP_COMMAND_RESPONSE_HEARTBEAT = 600;
	public static final int SKEP_COMMAND_RESPONSE_BIND_ID = 601;
	public static final int SKEP_COMMAND_RESPONSE_BIND_TAGS = 602;
	public static final int SKEP_COMMAND_RESPONSE_UNBIND_TAG = 603;
	public static final int SKEP_COMMAND_RESPONSE_API_CALL = 604;
	/*** Scala Akka Exchange Protocol Commands - end*/

}
