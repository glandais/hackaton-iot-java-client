package com.capgemini.csd.hackaton.client;

import java.util.Date;
import java.util.Random;
import java.util.UUID;

import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClient implements Client {

	public final static Logger LOGGER = LoggerFactory.getLogger(AbstractClient.class);

	protected static int THREADS = 10;

	protected static int SENSOR_TYPES = 10;

	protected static int MAX_VALUE = 10000;

	private static final ThreadLocal<Random> r = ThreadLocal.withInitial(() -> new Random());

	public static String getMessage(boolean randomTime) {
		return getMessage(null, randomTime ? null : System.currentTimeMillis(), null, null);
	}

	public static String getMessage(String messageId, Long date, Integer sensorType, Long value) {
		return "{ \"id\" : \"" + (messageId == null ? getMessageId() : messageId) + "\", \"timestamp\" : \""
				+ (date == null ? getMessageTimestamp() : getMessageTimestamp(date)) + "\", \"sensorType\" : "
				+ (sensorType == null ? getMessageSensorType().toString() : sensorType) + ", \"value\" : "
				+ (value == null ? getMessageValue().toString() : value) + "}";
	}

	public static String getMessageId() {
		return getUUID() + getUUID();
	}

	protected static String getUUID() {
		return UUID.randomUUID().toString().replaceAll("-", "");
	}

	public static Integer getMessageSensorType() {
		return r.get().nextInt(SENSOR_TYPES);
	}

	public static String getMessageTimestamp() {
		return getMessageTimestamp(new Date().getTime() - 10000 + r.get().nextInt(20000));
	}

	public static String getMessageTimestamp(long instant) {
		return ISODateTimeFormat.dateTime().print(instant);
	}

	public static Long getMessageValue() {
		if (MAX_VALUE == -1) {
			return r.get().nextLong();
		} else {
			return (long) r.get().nextInt(MAX_VALUE);
		}
	}

	protected String host;

	protected int port;

	public AbstractClient() {
		super();
	}

	@Override
	public void setHostPort(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Override
	public boolean sendMessage(boolean randomTime) {
		return sendMessage(getMessage(randomTime));
	}

}
