package com.capgemini.csd.hackaton.client;

public interface Client {

	void setHostPort(String host, int port);

	void sendMessages(int count, boolean randomTime);

	boolean sendMessage(String message);

	boolean sendMessage(boolean randomTime);

	String getSynthese(long start, int duration);

	void shutdown();

}
