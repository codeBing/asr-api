package com.ecarx.asrapi.service;

import com.alibaba.fastjson.JSONObject;
import com.ecarx.asrapi.interfaces.domain.NLUResponse;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author ITACHY
 * @date 2018/11/5
 * @desc TO-DO
 */

@Service
public class NLUService {

	private static Logger log = LoggerFactory.getLogger(NLUService.class);

	private double version  = 1.0;
	private String protocol = "cellphone";
	private String url      = "http://ai.ecarx.com.cn/test/ai/";

	/*private static WebClient webClient;

	static {
		ReactorClientHttpConnector connector = new ReactorClientHttpConnector(options ->
				options.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300000)
						//.compression(true)
						.afterNettyContextInit(ctx -> {
							ctx.addHandlerLast(new ReadTimeoutHandler(300000, TimeUnit.MILLISECONDS));
						}));
		webClient = WebClient.builder().clientConnector(connector).build();
	}*/

	private static OkHttpClient httpClient;

	static {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		builder.connectTimeout(300000, TimeUnit.MILLISECONDS);
		httpClient = builder.build();
	}

	public String dialog(String device, String uid, String input) {

		String domain = null, nlu = null;
		try {
			String     result      = login(webClient, device, uid);
			JSONObject jsonObject  = JSONObject.parseObject(result);
			String     accessToken = jsonObject.getString("accessToken");
			log.info("Token: {}", accessToken);
			//这里车机才需要fetch
			//fetch(webClient, accessToken);
			talk(webClient, input, accessToken);
			// 这里获取服务器的数据
			nlu = fetch(webClient, accessToken);
			log.info("nlu: {}", nlu);
			logout(accessToken, device);
			log.error("nul result: {}", nlu);
			jsonObject = JSONObject.parseObject(nlu);
			domain = jsonObject.getString("domain");
			if (StringUtils.isEmpty(domain)) {
				return dialog(device, uid, input);
			}
		} catch (Exception e) {
			log.error("Exception occur, error msg: ", e);
			/*if (StringUtils.isEmpty(domain)) {
				return dialog(device, uid, input);
			}*/
			return null;
		}
		return nlu;
	}

	public String login(WebClient webClient, String device, String uid) {
		JSONObject json   = new JSONObject();
		JSONObject client = new JSONObject();
		client.put("deviceid", device);
		client.put("uid", uid);
		client.put("timezone", "Asia/Shanghai");
		client.put("osFamily", "android");
		client.put("osType", "VEHICLE");
		client.put("osVersion", "android6.0");
		client.put("appVersion", "android");

		JSONObject location = new JSONObject();
		location.put("lng", 116.407394);
		location.put("lat", 39.904211);
		location.put("coordsType", "GCJ02");
		client.put("location", location);

		json.put("clientinfo", client);
		json.put("type", "login");

		String  result  = null;
		Request request = new Request.Builder().url(url + "login?protocol=" + protocol + "&version=" + version).build();
		try {
			Response response = httpClient.newCall(request).execute();
			result = response.body().string();
		} catch (Exception e) {
			log.error("NLU Loing request falied, error msg: ", e);
			return result;
		}
		return result;
	}

	public void logout(String accessToken, String device) {
		JSONObject json   = new JSONObject();
		JSONObject client = new JSONObject();
		client.put("deviceid", device);
		client.put("accessToken", accessToken);

		json.put("clientinfo", client);
		json.put("type", "logout");

		Request request = new Request.Builder().url(url + "login?protocol=" + protocol + "&version=" + version).build();
		try {
			Response response = httpClient.newCall(request).execute();
			result = response.body().string();
		} catch (Exception e) {
			log.error("NLU Loing request falied, error msg: ", e);
			return result;
		}

		WebClient.RequestBodySpec reqBody = webClient.method(HttpMethod.POST)
				.uri(url + "logout?ak=" + accessToken)
				.headers(header -> {});
		Mono<String> result = reqBody.contentType(MediaType.APPLICATION_JSON_UTF8)
				.syncBody(json.toString())
				.retrieve()
				.bodyToMono(String.class);
		log.info(result.block());
	}

	public void talk(WebClient webClient, String text, String accessToken) {
		JSONObject json       = new JSONObject();
		JSONObject clientinfo = new JSONObject();

		JSONObject location = new JSONObject();
		location.put("lng", 116.407394);
		location.put("lat", 39.904211);
		location.put("coordsType", "GCJ02");
		clientinfo.put("location", location);

		json.put("clientinfo", clientinfo);
		json.put("type", "talk");
		json.put("q", text);
		json.put("source", 1);

		String uri = new StringBuilder(url)
				.append("talk?protocol=")
				.append(protocol)
				.append("&version=")
				.append(version)
				.append("&ak=")
				.append(accessToken).toString();
		WebClient.RequestBodySpec reqBody = webClient.method(HttpMethod.POST)
				.uri(uri)
				.headers(header -> {});
		Mono<String> result = reqBody.contentType(MediaType.APPLICATION_JSON_UTF8)
				.syncBody(json.toString())
				.retrieve()
				.bodyToMono(String.class);

		log.info(result.block());
	}

	public String fetch(WebClient webClient, String accessToken) {

		String uri = new StringBuilder(url)
				.append("fetch?protocol=")
				.append(protocol)
				.append("&version=")
				.append(version)
				.append("&ak=")
				.append(accessToken)
				.toString();
		WebClient.RequestBodySpec reqBody = webClient.method(HttpMethod.GET)
				.uri(uri)
				.headers(header -> {});
		Mono<String> result = reqBody.retrieve().bodyToMono(String.class);
		String       body   = result.block();
		log.info("NLU Result: {}", body);
		return body;
	}

}
