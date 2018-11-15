package com.ecarx.asrapi.service;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.domain.ASRResponse;
import com.ecarx.asrapi.dto.nano.ASR;
import com.google.protobuf.nano.MessageNano;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc Http request service
 */
@Slf4j
@Service
public class HttpService {

	private final ASRConfig config;

	private final ThreadPoolExecutor executor;

	private static final int TIME_OUT = 30000;

	private static final OkHttpClient httpUpClient;

	static {
		OkHttpClient.Builder upClientBuilder = new OkHttpClient.Builder()
				.readTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
				.writeTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
				.connectTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
				.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
		httpUpClient = upClientBuilder.build();
	}

	public HttpService(final ASRConfig config) {

		this.config = config;
		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc handle asr up request
	 */
	public void handleASRUp(String id, LinkedBlockingQueue<ASR.APIRequest> requests) {

		//handle up steam
		RequestBody body = new RequestBody() {
			@Nullable
			@Override
			public MediaType contentType() {
				return null;
			}

			@Override
			public void writeTo(BufferedSink sink) {
				try {
					ASR.APIRequest request = requests.poll(config.getTimeout(), TimeUnit.MILLISECONDS);
					while (null != request) {
						int    type  = request.apiReqType;
						byte[] bytes = MessageNano.toByteArray(request);
						sink.writeIntLe(bytes.length);
						sink.write(bytes);
						sink.flush();
						if (ASR.API_REQ_TYPE_LAST == type || ASR.API_REQ_TYPE_CANCEL == type) {
							break;
						}
						request = requests.poll(30000, TimeUnit.MILLISECONDS);
					}
				} catch (Exception e) {
					log.error("Build ASR-Request body failed, error msg: ", e);
				}
			}
		};
		Headers headers = buildUpHeader();
		String  url     = config.getUrl() + "/up?id=" + id;
		executor.execute(() -> handlePostASR(url, body, headers, null));
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc handle asr up request
	 */
	public void handleASRUp(String id, final RequestBody body) {
		//handle up steam
		Headers headers = buildUpHeader();
		String  url     = config.getUrl() + "/up?id=" + id;
		//handlePostASR(url, body, headers, null);
		executor.execute(() -> handlePostASR(url, body, headers, null));
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/8
	 * @desc handle asr down request
	 */
	public LinkedBlockingQueue<ASR.APIResponse> handleASRDown(String id, RequestBody body) {

		LinkedBlockingQueue<ASR.APIResponse> responses = new LinkedBlockingQueue<>();
		//handle down stream
		String  url     = config.getUrl() + "/down?id=" + id;
		Headers headers = buildDownHeader();

		BiConsumer<Buffer, Long> callBack = (sink, byteCount) -> {
			try {
				while (sink.size() > 4) {
					long            len      = sink.readIntLe();
					byte[]          data     = sink.readByteArray(len);
					ASR.APIResponse response = ASR.APIResponse.parseFrom(data);
					responses.add(response);
				}
			} catch (Exception e) {
				log.error("Read response failed. error msg: ", e);
			}
		};
		executor.execute(() -> handlePostASR(url, body, headers, callBack));
		return responses;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc send all requests by POST method
	 */
	private void handlePostASR(String url, RequestBody body, Headers headers, BiConsumer<Buffer, Long> callBack) {
		OkHttpClient httpClient = httpUpClient;
		if (null != callBack) {
			OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
			clientBuilder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
			clientBuilder.addNetworkInterceptor(chain -> {
				Response response = chain.proceed(chain.request());
				return response.newBuilder().body(new ASRResponse(response.body(), callBack)).build();
			});
			httpClient = clientBuilder.build();
		}
		Request request = new Request.Builder().url(url).post(body).headers(headers).build();

		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("ASR request failed, fail msg: ", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				//trigger read response
				if (null != callBack) {
					log.info(url + ", resp_code:" + response.code());
					log.info(url + ", resp_message:" + response.message());
					log.info(url + ", resp_Transfer-Encoding:" + response.header("Transfer-Encoding"));
					log.info(url + ", resp_protocol:" + response.protocol());
					log.info(url + ", response text: ", response.body().string());
				}
			}
		});
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build up header
	 */
	private Headers buildUpHeader() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Transfer-Encoding", "chunked");
		return buildHeader(headers);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build down header
	 */
	private Headers buildDownHeader() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/octet-stream");
		headers.put("Accept-Encoding", "gzip,deflate");
		return buildHeader(headers);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc header actually build here
	 */
	private Headers buildHeader(Map<String, String> headers) {

		Headers.Builder headerBiulder = new Headers.Builder();
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			headerBiulder.add(entry.getKey(), entry.getValue());
		}
		return headerBiulder.build();
	}
}
