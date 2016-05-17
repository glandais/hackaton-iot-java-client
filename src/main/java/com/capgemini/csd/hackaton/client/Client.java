package com.capgemini.csd.hackaton.client;

import java.util.Map;

public interface Client {

	void setHostPort(String host, int port);

	void sendMessages(int count, boolean randomTime);

	boolean sendMessage(String message);

	boolean sendMessage(boolean randomTime);

	String getSyntheseDistante(long start, int duration);

	void shutdown();

	Map<Integer, Summary> getSyntheseLocale(long timestamp, Integer duration);

	void razMessages();

	void indexMessage(long timestamp, int sensorType, long value);

}
