package com.ecarx.asrapi.service;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.consts.EnvConsts;
import com.ecarx.asrapi.domain.ASRResponse;
import com.ecarx.asrapi.dto.nano.ASR;
import com.google.protobuf.nano.MessageNano;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
public class WebService {

	private final ASRConfig config;

	private final ParamService paramService;

	private final ThreadPoolExecutor executor;

	private OkHttpClient httpClient;

	private final TrustManager[] trustAllCerts = new TrustManager[]{
			new X509TrustManager() {
				@Override
				public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
				}

				@Override
				public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
				}

				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return new java.security.cert.X509Certificate[]{};
				}
			}
	};

	private final HostnameVerifier verifiedAllHostname = (hostname, session) -> true;

	private SSLContext       sslContext;
	private SSLSocketFactory sslSocketFactory;

	@Autowired
	public WebService(final ASRConfig config, final ParamService paramService) throws Exception {

		this.config = config;
		this.paramService = paramService;
		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());

		sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
		sslSocketFactory = sslContext.getSocketFactory();

		OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
		clientBuilder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
				.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
				.hostnameVerifier(verifiedAllHostname)
				.connectTimeout(60, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.writeTimeout(60, TimeUnit.SECONDS)
				.retryOnConnectionFailure(true);
		httpClient = clientBuilder.build();
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc only for local test
	 */
	public LinkedBlockingQueue<ASR.APIResponse> handleASR(String type, byte[] data) {
		if ("local".equals(type)) {
			String url = config.getLocal();
			log.info("初始化Url: {}", url);
			return handleHttp2(EnvConsts.ASR_DATA, data, url);
		} else if ("json".equals(type)) {
			String url = config.getJson();
			log.info("初始化Url: {}", url);
			return handleHttp2(EnvConsts.ASR_DATA, data, url);
		} else {

			String url = config.getBaidu();
			log.info("初始化Url: {}", url);
			return handleHttp2(EnvConsts.ASR_DATA, data, url);
		}
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc only for local test
	 */
	private LinkedBlockingQueue<ASR.APIResponse> handleHttp2(String type, byte[] data, String url) {
		String id = UUID.randomUUID().toString();
		log.info("---构建数据---");
		LinkedBlockingQueue<ASR.APIResponse> responses = new LinkedBlockingQueue<>();
		//handle up steam
		executor.execute(() -> handleUpStream(url + "/up?id=" + id, type, data));
		//handle down stream
		executor.execute(() -> handleDownStream(url + "/down?id=" + id, responses));
		return responses;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc handle up stream
	 */
	private void handleUpStream(String url, String type, byte[] data) {

		Headers              headers  = buildUpHeader();
		List<ASR.APIRequest> requests = new ArrayList<>();
		switch (type) {
		case EnvConsts.ASR_AUTH:
			requests.addAll(paramService.buildActv());
			break;
		case EnvConsts.ASR_DATA:
			requests.addAll(paramService.buildData(data));
			break;
		case EnvConsts.ASR_AUTH_WITH_AUDIO:
			requests.addAll(paramService.buildActvWithAudio(data));
			break;
		}

		RequestBody body = new RequestBody() {
			@Nullable
			@Override
			public MediaType contentType() {
				return null;
			}

			@Override
			public void writeTo(BufferedSink sink) {
				requests.forEach(request -> {
					if (null != request) {
						byte[] bytes = MessageNano.toByteArray(request);
						try {
							sink.writeIntLe(bytes.length);
							sink.write(bytes);
							sink.flush();
							TimeUnit.MILLISECONDS.sleep(100);
						} catch (Exception e) {
							log.error("Param write error: ", e);
						}
					}
				});
			}
		};
		handlePostASR(url, body, headers, null);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc as server to send down stream
	 */
	private void handleDownStream(String url, LinkedBlockingQueue<ASR.APIResponse> responses) {

		Headers headers = buildDownHeader();
		try {
			TimeUnit.MILLISECONDS.sleep(1000);
		} catch (Exception e) {
			log.error("sleep error, error msg: ", e);
		}
		handlePostASR(url, new FormBody.Builder().build(), headers, (sink, byteCount) -> {
			try {
				while (sink.size() > 4) {
					log.info("Sink text: ", sink.toString());
					long            len         = sink.readIntLe();
					byte[]          data        = sink.readByteArray(len);
					ASR.APIResponse apiResponse = ASR.APIResponse.parseFrom(data);
					responses.add(apiResponse);
					log.info("ASR response：" + apiResponse.toString());
					if (apiResponse.type == 5) {
						log.info("从百度拿来的结果结束!");
						break;
					}
				}
			} catch (Exception e) {
				log.error("Read response failed. error msg: ", e);
			}
		});
	}

	private void handlePostASR(String url, RequestBody body, Headers headers, BiConsumer<Buffer, Long> callBack) {
		OkHttpClient httpClient = this.httpClient;
		if (null != callBack) {
			OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
			clientBuilder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
					.sslSocketFactory(this.sslSocketFactory, (X509TrustManager) trustAllCerts[0])
					.hostnameVerifier(verifiedAllHostname)
					.connectTimeout(60, TimeUnit.SECONDS)
					.readTimeout(60, TimeUnit.SECONDS)
					.writeTimeout(60, TimeUnit.SECONDS)
					.retryOnConnectionFailure(true);
			clientBuilder.addNetworkInterceptor(chain -> {
				Response response = chain.proceed(chain.request());
				return response.newBuilder().body(new ASRResponse(response.body(), callBack)).build();
			});
			httpClient = clientBuilder.build();
		}

		Request.Builder requestBuilder = new Request.Builder();
		requestBuilder.url(url).post(body);
		if (null != headers) {
			requestBuilder.headers(headers);
		}
		Request request = requestBuilder.build();
		Call    call    = httpClient.newCall(request);

		call.enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("ASR request failed, fail msg: ", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (null != callBack) {
					//trigger read response
					log.info(url + ", resp_code:" + response.code());
					log.info(url + ", resp_message:" + response.message());
					log.info(url + ", resp_Transfer-Encoding:" + response.header("Transfer-Encoding"));
					log.info(url + ", resp_protocol:" + response.protocol());
					log.info(url + "response text: ", response.body().string());
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

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc build error response for occuring exception
	 */
	private List<ASR.APIResponse> buildFailResponse(String msg) {
		List<ASR.APIResponse> responses = new ArrayList<>();
		//build result response
		ASR.APIResponse response = new ASR.APIResponse();
		response.type = ASR.API_RESP_TYPE_RES;
		response.errNo = -3003;
		response.errMsg = msg;
		responses.add(response);
		//build last response
		response = new ASR.APIResponse();
		response.type = ASR.API_RESP_TYPE_LAST;
		responses.add(response);
		return responses;
	}
}
