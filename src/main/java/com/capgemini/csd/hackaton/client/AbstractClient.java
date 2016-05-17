package com.capgemini.csd.hackaton.client;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import java.util.Date;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClient implements Client {

	public final static Logger LOGGER = LoggerFactory.getLogger(AbstractClient.class);

	protected static int THREADS = 10;

	protected static int SENSOR_TYPES = 10;

	protected static int MAX_VALUE = 10000;

	private static final ThreadLocal<Random> r = ThreadLocal.withInitial(() -> new Random());

	private AtomicLong currentId = new AtomicLong();

	private NavigableMap<UUID, UUID> messages;

	private ReentrantLock lock = new ReentrantLock();

	public static String getMessage(boolean randomTime) {
		return getMessage(randomTime, null);
	}

	public static String getMessage(boolean randomTime, Client client) {
		return getMessage(null, randomTime ? null : System.currentTimeMillis(), null, null, client);
	}

	public static String getMessage(String idParam, Long dateParam, Integer sensorTypeParam, Long valueParam) {
		return getMessage(idParam, dateParam, sensorTypeParam, valueParam, null);
	}

	public static String getMessage(String idParam, Long dateParam, Integer sensorTypeParam, Long valueParam,
			Client client) {
		String id = idParam == null ? getMessageId() : idParam;
		long timestamp = dateParam == null ? getTimestamp() : dateParam;
		String date = getMessageTimestamp(timestamp);
		Integer sensorType = sensorTypeParam == null ? getMessageSensorType() : sensorTypeParam;
		Long value = valueParam == null ? getMessageValue() : valueParam;
		if (client != null) {
			client.indexMessage(timestamp, sensorType, value);
		}
		return "{ \"id\" : \"" + id + "\", \"timestamp\" : \"" + date + "\", \"sensorType\" : " + sensorType
				+ ", \"value\" : " + value + "}";
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
		return getMessageTimestamp(getTimestamp());
	}

	protected static long getTimestamp() {
		return new Date().getTime() - 10000 + r.get().nextInt(20000);
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

	public void razMessages() {
		messages = new TreeMap<>();
	}

	public void indexMessage(long timestamp, int sensorType, long value) {
		UUID timeUUID = new UUID(timestamp, currentId.getAndIncrement());
		UUID valueUUID = new UUID(sensorType, value);
		lock.lock();
		try {
			messages.put(timeUUID, valueUUID);
		} finally {
			lock.unlock();
		}
	}

	public Map<Integer, Summary> getSyntheseLocale(long timestamp, Integer duration) {
		long lo = timestamp;
		long hi = timestamp + duration * 1000;
		UUID from = new UUID(lo, Long.MIN_VALUE);
		UUID to = new UUID(hi, Long.MAX_VALUE);

		Stream<UUID> stream = messages.subMap(from, to).values().stream();

		Map<Integer, Summary> synthese = stream
				.collect(groupingBy(uuid -> (int) uuid.getMostSignificantBits(), mapping(UUID::getLeastSignificantBits,
						Collector.of(Summary::new, Summary::accept, Summary::combine2))));

		return synthese;
	}

}
