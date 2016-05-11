package com.capgemini.csd.hackaton.client;

import java.io.IOException;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientAsyncHTTP extends AbstractClient {

	public final static Logger LOGGER = LoggerFactory.getLogger(ClientAsyncHTTP.class);

	private final AsyncHttpClient httpClient;

	public ClientAsyncHTTP() {
		super();
		AsyncHttpClientConfig cf = new DefaultAsyncHttpClientConfig.Builder().build();
		httpClient = new DefaultAsyncHttpClient(cf);
	}

	@Override
	public boolean sendMessage(String json) {
		try {
			LOGGER.debug("Sending " + json);
			Response response = httpClient.preparePost("http://" + host + ":" + port + "/messages")
					.addHeader("Content-Type", "application/json").setBody(json).execute().get();
			LOGGER.debug("Response " + response.getStatusCode());
			if (response.getStatusCode() != 200) {
				throw new IllegalStateException("Erreur! " + response);
			}
			return true;
		} catch (Exception e) {
			LOGGER.debug("Echec à l'envoi du message", e);
			return false;
		}
	}

	@Override
	public String getSynthese(long start, int duration) {
		try {
			//			LOGGER.info("Sending getSynthese");
			String response = httpClient.prepareGet("http://" + host + ":" + port + "/messages/synthesis")
					.addQueryParam("timestamp", getMessageTimestamp(start)).addQueryParam("duration", duration + "")
					.execute().get().getResponseBody();
			//			LOGGER.info("Response " + response);
			return response;
		} catch (Exception e) {
			LOGGER.error("Echec", e);
			return "";
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		try {
			httpClient.close();
		} catch (IOException e) {
			LOGGER.error("Erreur", e);
		}
	}

}
