package com.capgemini.csd.hackaton.client;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import io.airlift.airline.Command;
import io.airlift.airline.Option;
import io.airlift.airline.OptionType;

@Command(name = "client", description = "Client")
public class ExecutionClient implements Runnable {

	private static final int SENSOR_LIMITE = 1000;

	public final static Logger LOGGER = LoggerFactory.getLogger(ExecutionClient.class);

	@Option(type = OptionType.GLOBAL, name = "-host", description = "Host (défaut : 192.168.1.1)")
	public String host = "192.168.1.1";

	@Option(type = OptionType.GLOBAL, name = "-port", description = "Port (défaut : 80)")
	public int port = 80;

	@Option(type = OptionType.GLOBAL, name = "-nombreLots", description = "Nombre de lots de messages (défaut : 100)")
	public int n = 1000;

	@Option(type = OptionType.GLOBAL, name = "-messagesLot", description = "Nombre de messages par lot (défaut : 1000)")
	public int m = 1000;

	@Option(type = OptionType.GLOBAL, name = "-synthese", description = "Tests de la synthese (défaut : false)")
	public boolean synthese = false;

	@Option(type = OptionType.GLOBAL, name = "-synthesesLot", description = "Nombre de syntheses par lot (défaut : 10)")
	public int syntheses = 10;

	@Option(type = OptionType.GLOBAL, name = "-limites", description = "Tests de cas au limites (défaut : false)")
	public boolean limite = false;

	@Option(type = OptionType.GLOBAL, name = "-sensors", description = "Nombre de sensors (défaut : 10)")
	public int sensors = 10;

	@Option(type = OptionType.GLOBAL, name = "-threads", description = "Nombre de threads (défaut : 10)")
	public int threads = 10;

	@Option(type = OptionType.GLOBAL, name = "-maxValue", description = "value max (-1 pour toute la plage de long)  (défaut : 10000)")
	public int maxValue = 10000;

	private ThreadLocal<Random> r = ThreadLocal.withInitial(() -> new Random());

	private AtomicLong currentId = new AtomicLong();

	private NavigableMap<UUID, UUID> messages;

	private ReentrantLock lock = new ReentrantLock();

	private ClientAsyncHTTP client;

	@Override
	public void run() {
		ClientAsyncHTTP.MAX_VALUE = maxValue;
		ClientAsyncHTTP.SENSOR_TYPES = sensors;
		AbstractClient.THREADS = threads;

		client = new ClientAsyncHTTP();
		client.setHostPort(host, port);
		messages = new TreeMap<>();
		testMoyenne();
		if (limite) {
			testLimites();
		}
		messages = new TreeMap<>();
		Stopwatch sw = Stopwatch.createStarted();
		for (int i = 0; i < n; i++) {
			long start = System.currentTimeMillis();
			client.sendMessages(m, true);
			if (synthese) {
				verifierSynthese(start);
			}
		}
		LOGGER.info(sw.toString());
		LOGGER.info((n * m) + " messages envoyés");
		client.shutdown();
		System.exit(0);
	}

	protected void verifierSynthese(long startParam) {
		// on agrandit la plage pour les messages avec un timestamp aléatoire
		long start = startParam - 10000;
		long diff = System.currentTimeMillis() - start + 20000;
		for (int i = 0; i < syntheses; i++) {
			long duration = 1000 + r.get().nextInt((int) diff - 2500);
			long relativeStart = r.get().nextInt((int) (diff - duration));
			long syntheseStart = start + relativeStart;
			int durationSecondes = (int) (duration / 1000);
			Map<Integer, JsonObject> remoteSynthese = getSyntheseServeur(syntheseStart, durationSecondes);
			Map<Integer, Summary> local = getSyntheseClient(syntheseStart, durationSecondes);
			for (Entry<Integer, Summary> entry : local.entrySet()) {
				assertEquals("min " + entry.getKey(), entry.getValue().getMin(),
						getValeurSynthese(remoteSynthese, entry.getKey(), "minValue"));
				assertEquals("max " + entry.getKey(), entry.getValue().getMax(),
						getValeurSynthese(remoteSynthese, entry.getKey(), "maxValue"));
				assertEquals("avg " + entry.getKey(), entry.getValue().getAverage(),
						getValeurSynthese(remoteSynthese, entry.getKey(), "mediumValue"));
			}
		}
	}

	protected void testLimites() {
		testMoyenne();

		long start = System.currentTimeMillis();
		LOGGER.info("Envoi de deux messages avec des valeurs très éloignées");
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, Long.MIN_VALUE));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, -1000L));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, 1L));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, 1000L));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, 1000L));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, -1000L));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, Long.MAX_VALUE));
		Map<Integer, JsonObject> remoteSynthese = getSyntheseServeur(start, 10);
		assertEquals("Mauvaise moyenne", 0L, getValeurSynthese(remoteSynthese, SENSOR_LIMITE, "mediumValue"));

		LOGGER.info("Envoi du message regreergregregre");
		client.sendMessage(AbstractClient.getMessage("regreergregregre", null, SENSOR_LIMITE, null));

		LOGGER.info("Envoi de 2 messages identiques");
		String sameId = AbstractClient.getMessageId();
		client.sendMessage(AbstractClient.getMessage(sameId, null, SENSOR_LIMITE, null));
		if (!client.sendMessage(AbstractClient.getMessage(sameId, null, SENSOR_LIMITE, null))) {
			LOGGER.info("Id dupliqué détecté");
		} else {
			LOGGER.error("Id dupliqué non détecté");
		}

		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			ids.add(AbstractClient.getMessageId());
		}
		ExecutorService executor = Executors.newFixedThreadPool(10);
		List<Future<Boolean>> futures = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			for (String id : ids) {
				futures.add(executor
						.submit(() -> client.sendMessage(AbstractClient.getMessage(id, null, SENSOR_LIMITE, null))));
			}
		}
		try {
			int c = 0;
			for (Future<Boolean> future : futures) {
				if (future.get()) {
					c++;
				}
			}
			if (c == 5) {
				LOGGER.info("Id dupliqué détecté");
			} else {
				LOGGER.error("Id dupliqué non détecté");
			}
		} catch (Exception e) {
			LOGGER.error(":(", e);
		}
		executor.shutdownNow();
	}

	protected void testMoyenne() {
		LOGGER.info("Envoi de deux messages avec des très grandes valeurs");
		long start = System.currentTimeMillis();
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, Long.MAX_VALUE - 100));
		client.sendMessage(AbstractClient.getMessage(null, null, SENSOR_LIMITE, Long.MAX_VALUE - 200));
		Map<Integer, JsonObject> remoteSynthese = getSyntheseServeur(start, 10);
		assertEquals("Mauvaise moyenne", Long.MAX_VALUE - 150,
				getValeurSynthese(remoteSynthese, SENSOR_LIMITE, "mediumValue"));
	}

	protected Object getValeurSynthese(Map<Integer, JsonObject> remoteSynthese, Integer i, String key) {
		if (remoteSynthese.containsKey(i)) {
			return remoteSynthese.get(i).getJsonNumber(key).bigDecimalValue();
		}
		return null;
	}

	protected void assertEquals(String message, Object expected, Object actual) {
		if (expected instanceof Number && actual instanceof Number) {
			BigDecimal bg1 = new BigDecimal(expected.toString()).stripTrailingZeros();
			BigDecimal bg2 = new BigDecimal(actual.toString()).stripTrailingZeros();
			assertEqualsDo(message, bg1, bg2);
		} else {
			assertEqualsDo(message, expected, actual);
		}
	}

	protected void assertEqualsDo(String message, Object expected, Object actual) {
		if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
			LOGGER.error(message + " (attendu : " + expected + ", obtenu : " + actual + ")");
		}
	}

	protected void indexMessage(long timestamp, int sensorType, long value) {
		UUID timeUUID = new UUID(timestamp, currentId.getAndIncrement());
		UUID valueUUID = new UUID(sensorType, value);
		lock.lock();
		try {
			messages.put(timeUUID, valueUUID);
		} finally {
			lock.unlock();
		}
	}

	protected Map<Integer, JsonObject> getSyntheseServeur(long start, int duration) {
		String response = client.getSynthese(start, duration);
		JsonReader reader = Json.createReader(new ByteArrayInputStream(response.getBytes()));
		List<JsonValue> syntheses = reader.readArray();
		return syntheses.stream()
				.collect(Collectors.toMap(e -> ((JsonObject) e).getInt("sensorType"), e -> (JsonObject) e, (k, v) -> {
					throw new RuntimeException(String.format("Duplicate key %s", k));
				}, TreeMap::new));
	}

	protected Map<Integer, Summary> getSyntheseClient(long timestamp, Integer duration) {
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
