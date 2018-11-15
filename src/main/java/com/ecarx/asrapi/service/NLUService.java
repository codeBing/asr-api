package com.ecarx.asrapi.service;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author ITACHY
 * @date 2018/11/5
 * @desc TO-DO
 */

@Slf4j
@Service
public class NLUService {

	@Value("${nlu.version}")
	private double version;

	@Value("${nlu.protocol}")
	private String protocol;

	@Value("${nlu.url}")
	private String url;

	private static OkHttpClient httpClient;

	static {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		builder.connectTimeout(30000, TimeUnit.MILLISECONDS)
				.readTimeout(30000, TimeUnit.MILLISECONDS)
				.writeTimeout(30000, TimeUnit.MILLISECONDS);
		httpClient = builder.build();
	}

	public String dialog(String device, String uid, String text) {

		String domain = null, nlu = null;
		try {
			String     result      = login(device, uid);
			JSONObject jsonObject  = JSONObject.parseObject(result);
			String     accessToken = jsonObject.getString("accessToken");
			log.info("Token: {}", accessToken);
			if (null != accessToken) {
				talk(text, accessToken);
				// 这里获取服务器的数据
				nlu = fetch(accessToken);
				logout(accessToken, device);
				jsonObject = JSONObject.parseObject(nlu);
				domain = jsonObject.getString("domain");
				if (StringUtils.isEmpty(domain)) {
					return dialog(device, uid, text);
				}
			}
		} catch (Exception e) {
			log.error("Exception occur, error msg: ", e);
			if (StringUtils.isEmpty(domain)) {
				return dialog(device, uid, text);
			}
			return nlu;
		}
		return nlu;
	}

	public String login(String device, String uid) {

		JSONObject json   = new JSONObject();
		JSONObject client = new JSONObject();
		client.put("deviceid", device);
		client.put("uid", uid);
		client.put("timezone", "Asia/Shanghai");
		client.put("osFamily", "ANDROID");
		client.put("deviceType", "MOBILE");
		client.put("osVersion", "22");
		client.put("deviceModel", "1.0");
		client.put("appVersion", "1.0.0-SNAPSHOT");

		JSONObject location = new JSONObject();
		location.put("lng", 116.407394);
		location.put("lat", 39.904211);
		location.put("type", "GCJ02");
		client.put("location", location);

		json.put("clientinfo", client);
		json.put("type", "login");

		String uri = new StringBuilder(url)
				.append("login?protocol=").append(protocol)
				.append("&version=").append(version)
				.toString();

		RequestBody body    = FormBody.create(MediaType.parse("application/json"), json.toJSONString());
		Request     request = new Request.Builder().url(uri).post(body).build();
		try {
			Response response = httpClient.newCall(request).execute();
			return response.body().string();
		} catch (Exception e) {
			log.error("NLU Login request falied, error msg: ", e);
			return null;
		}
	}

	public void logout(String accessToken, String device) {
		JSONObject json   = new JSONObject();
		JSONObject client = new JSONObject();
		client.put("deviceid", device);
		client.put("accessToken", accessToken);

		json.put("clientinfo", client);
		json.put("type", "logout");

		RequestBody body    = FormBody.create(MediaType.parse("application/json"), json.toJSONString());
		Request     request = new Request.Builder().url(url + "logout?ak=" + accessToken).post(body).build();

		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("Logout Failed, error msg: ", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				log.info("Logout Success!, msg: {}", response.body().string());
			}
		});
	}

	public void talk(String text, String accessToken) {
		log.info("Talk: {}", text);
		JSONObject json = new JSONObject();
		json.put("clientinfo", new JSONObject());
		json.put("type", "text_input");
		json.put("q", text);
		json.put("source", 1);

		String uri = new StringBuilder(url)
				.append("talk?&ak=")
				.append(accessToken).toString();

		RequestBody body    = FormBody.create(MediaType.parse("application/json"), json.toJSONString());
		Request     request = new Request.Builder().url(uri).post(body).build();

		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("Logout Failed, error msg: ", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				log.info("Logout Success!, msg: {}", response.body().string());
			}
		});
	}

	public String fetch(String accessToken) {

		String uri = new StringBuilder(url)
				.append("fetch?protocol=").append(protocol)
				.append("&version=").append(version)
				.append("&ak=").append(accessToken)
				.toString();
		Request request = new Request.Builder().url(uri).get().build();
		try {
			Response response = httpClient.newCall(request).execute();
			String   result   = response.body().string();
			log.info("NLU fetch msg: {}", result);
			return result;
		} catch (Exception e) {
			log.error("NLU Fetch request falied, error msg: ", e);
			return null;
		}
	}

}
