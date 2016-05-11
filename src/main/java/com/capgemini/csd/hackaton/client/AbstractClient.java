package com.capgemini.csd.hackaton.client;

import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

	private ExecutorService executor;

	protected String host;

	protected int port;

	public AbstractClient() {
		super();
		LOGGER.info("Initialisation avec " + THREADS + " threads.");
		executor = Executors.newFixedThreadPool(THREADS);
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

	@Override
	public void sendMessages(int count, boolean randomTime) {
		LOGGER.info("Envoi de " + count + " messages.");
		Future<?>[] futures = new Future<?>[count];
		long start = System.nanoTime();
		for (int i = 0; i < count; i++) {
			futures[i] = executor.submit(() -> sendMessage(randomTime));
		}
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("?", e);
			}
		}
		long end = System.nanoTime();
		double diff = (end - start) / 1000000000.0;
		double rate = count / diff;
		LOGGER.info(rate + " messages/s");
	}

	public void shutdown() {
		LOGGER.info("Fermeture du pool d'exécution.");
		executor.shutdownNow();
	}

}
