package com.capgemini.csd.hackaton.client;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

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

	@Option(type = OptionType.GLOBAL, name = "-nombreLots", description = "Nombre de lots de messages (défaut : 1000)")
	public int n = 1000;

	@Option(type = OptionType.GLOBAL, name = "-messagesLot", description = "Nombre de messages par lot (défaut : 1000)")
	public int m = 1000;

	@Option(type = OptionType.GLOBAL, name = "-syntheses", description = "Nombre de syntheses par lot (défaut : 10)")
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

	private ClientAsyncHTTP client;

	@Override
	public void run() {
		ClientAsyncHTTP.MAX_VALUE = maxValue;
		ClientAsyncHTTP.SENSOR_TYPES = sensors;
		AbstractClient.THREADS = threads;

		client = new ClientAsyncHTTP();
		client.setHostPort(host, port);
		client.razMessages();
		if (limite) {
			testLimites();
		}
		client.razMessages();
		Stopwatch sw = Stopwatch.createStarted();
		for (int i = 0; i < n; i++) {
			long start = System.currentTimeMillis();
			client.sendMessages(m, false);
			verifierSynthese(start);
		}
		LOGGER.info(sw.toString());
		LOGGER.info((n * m) + " messages envoyés");
		client.shutdown();
		System.exit(0);
	}

	protected void verifierSynthese(long startParam) {
		if (syntheses == 0) {
			return;
		}
		// on agrandit la plage pour les messages avec un timestamp aléatoire
		long start = startParam - 10000;
		long diff = System.currentTimeMillis() - start + 20000;
		long startSyntheses = System.nanoTime();
		for (int i = 0; i < syntheses; i++) {
			long duration = 1000 + r.get().nextInt((int) diff - 2500);
			long relativeStart = r.get().nextInt((int) (diff - duration));
			long syntheseStart = start + relativeStart;
			int durationSecondes = (int) (duration / 1000);
			Map<Integer, JsonObject> remoteSynthese = getSyntheseServeur(syntheseStart, durationSecondes);
			Map<Integer, Summary> local = client.getSyntheseLocale(syntheseStart, durationSecondes);
			testEq(remoteSynthese, local);
		}
		long endSyntheses = System.nanoTime();
		double diffSyntheses = (endSyntheses - startSyntheses) / 1000000000.0;
		double rate = syntheses / diffSyntheses;
		LOGGER.info(rate + " synthese/s");
	}

	protected boolean testEq(Map<Integer, JsonObject> remoteSynthese, Map<Integer, Summary> local) {
		boolean eq = true;
		for (Entry<Integer, Summary> entry : local.entrySet()) {
			eq = assertEquals("min " + entry.getKey(), entry.getValue().getMin(),
					getValeurSynthese(remoteSynthese, entry.getKey(), "minValue")) && eq;
			eq = assertEquals("max " + entry.getKey(), entry.getValue().getMax(),
					getValeurSynthese(remoteSynthese, entry.getKey(), "maxValue")) && eq;
			eq = assertEquals("avg " + entry.getKey(), entry.getValue().getAverage(),
					getValeurSynthese(remoteSynthese, entry.getKey(), "mediumValue")) && eq;
		}
		return eq;
	}

	protected void testLimites() {
		testMoyenne1();
		testMoyenne2();
		testPersistence();
		testDuplique1();
		testDuplique2();
	}

	protected void testPersistence() {
		LOGGER.info("Envoi du message regreergregregre");
		client.sendMessage(AbstractClient.getMessage("regreergregregre", null, SENSOR_LIMITE, null));
	}

	protected void testMoyenne1() {
		LOGGER.info("Envoi de deux messages avec des très grandes valeurs");
		long start = System.currentTimeMillis();
		client.sendMessage(AbstractClient.getMessage(null, start, SENSOR_LIMITE, Long.MAX_VALUE - 100));
		client.sendMessage(AbstractClient.getMessage(null, start + 10, SENSOR_LIMITE, Long.MAX_VALUE - 200));
		Map<Integer, JsonObject> remoteSynthese = getSyntheseServeur(start, 10);
		assertEquals("Mauvaise moyenne", Long.MAX_VALUE - 150,
				getValeurSynthese(remoteSynthese, SENSOR_LIMITE, "mediumValue"));
	}

	protected void testMoyenne2() {
		long start = System.currentTimeMillis();
		LOGGER.info("Envoi de deux messages avec des valeurs très éloignées");
		client.sendMessage(AbstractClient.getMessage(null, start, SENSOR_LIMITE, Long.MIN_VALUE));
		client.sendMessage(AbstractClient.getMessage(null, start + 1, SENSOR_LIMITE, -1000L));
		client.sendMessage(AbstractClient.getMessage(null, start + 2, SENSOR_LIMITE, 1L));
		client.sendMessage(AbstractClient.getMessage(null, start + 3, SENSOR_LIMITE, 1000L));
		client.sendMessage(AbstractClient.getMessage(null, start + 4, SENSOR_LIMITE, 1000L));
		client.sendMessage(AbstractClient.getMessage(null, start + 5, SENSOR_LIMITE, -1000L));
		client.sendMessage(AbstractClient.getMessage(null, start + 6, SENSOR_LIMITE, Long.MAX_VALUE));
		Map<Integer, JsonObject> remoteSynthese = getSyntheseServeur(start, 10);
		assertEquals("Mauvaise moyenne", 0L, getValeurSynthese(remoteSynthese, SENSOR_LIMITE, "mediumValue"));
	}

	protected void testDuplique1() {
		LOGGER.info("Envoi de 2 messages identiques");
		String sameId = AbstractClient.getMessageId();
		client.sendMessage(AbstractClient.getMessage(sameId, null, SENSOR_LIMITE, null));
		if (!client.sendMessage(AbstractClient.getMessage(sameId, null, SENSOR_LIMITE, null))) {
			LOGGER.info("Id dupliqué détecté");
		} else {
			LOGGER.error("Id dupliqué non détecté");
		}
	}

	protected void testDuplique2() {
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

	protected Object getValeurSynthese(Map<Integer, JsonObject> remoteSynthese, Integer i, String key) {
		if (remoteSynthese.containsKey(i)) {
			return remoteSynthese.get(i).getJsonNumber(key).bigDecimalValue();
		}
		return null;
	}

	protected boolean assertEquals(String message, Object expected, Object actual) {
		if (expected instanceof Number && actual instanceof Number) {
			BigDecimal bg1 = new BigDecimal(expected.toString()).stripTrailingZeros();
			BigDecimal bg2 = new BigDecimal(actual.toString()).stripTrailingZeros();
			return assertEqualsDo(message, bg1, bg2);
		} else {
			return assertEqualsDo(message, expected, actual);
		}
	}

	protected boolean assertEqualsDo(String message, Object expected, Object actual) {
		if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
			LOGGER.error(message + " (attendu : " + expected + ", obtenu : " + actual + ")");
			return false;
		} else {
			return true;
		}
	}

	protected Map<Integer, JsonObject> getSyntheseServeur(long start, int duration) {
		String response = client.getSyntheseDistante(start, duration);
		if (response.length() == 0) {
			return Collections.emptyMap();
		}
		JsonReader reader = Json.createReader(new ByteArrayInputStream(response.getBytes()));
		List<JsonValue> syntheses = reader.readArray();
		return syntheses.stream()
				.collect(Collectors.toMap(e -> ((JsonObject) e).getInt("sensorType"), e -> (JsonObject) e, (k, v) -> {
					throw new RuntimeException(String.format("Duplicate key %s", k));
				}, TreeMap::new));
	}

}
