package com.ecarx.asrapi.service;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.consts.EnvConsts;
import com.ecarx.asrapi.domain.ASRResponse;
import com.ecarx.asrapi.dto.nano.ASR;
import com.google.protobuf.nano.MessageNano;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
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
@Service
public class HttpService {

	private static final Logger log = LoggerFactory.getLogger(HttpService.class);

	private final ASRConfig config;

	private final NLUService nluService;

	private final ParamService paramService;

	private final ThreadService threadService;

	private OkHttpClient httpClient;

	private OkHttpClient.Builder clientBuilder;

	@Autowired
	public HttpService(final ASRConfig config, final NLUService nluService, final ParamService paramService, ThreadService threadService) {

		this.config = config;
		this.nluService = nluService;
		this.paramService = paramService;
		this.threadService = threadService;

		this.clientBuilder = new OkHttpClient.Builder();
		this.clientBuilder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
		httpClient = clientBuilder.build();
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc only for local test
	 */
	public LinkedBlockingQueue<ASR.APIResponse> handleASR(byte[] data) {
		return handleHttp2(EnvConsts.ASR_DATA, data);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc only for local test
	 */
	private LinkedBlockingQueue<ASR.APIResponse> handleHttp2(String type, byte[] data) {
		String id = UUID.randomUUID().toString();

		LinkedBlockingQueue<ASR.APIResponse> responses = new LinkedBlockingQueue<>();
		//handle up steam
		threadService.execute(() -> handleUpStream(config.getUpUrl() + "?id=" + id, type, data));
		//handle down stream
		threadService.execute(() -> handleDownStream(config.getDownUrl() + "?id=" + id, responses));
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
	 * @desc handle asr request
	 */
	public void handleASRUp(String id, final RequestBody body) {
		//handle up steam
		threadService.execute(() -> handleUpStream(config.getUpUrl() + "?id=" + id, body));
	}

	public LinkedBlockingQueue<ASR.APIResponse> handleASRDown(String id, final RequestBody body) {

		LinkedBlockingQueue<ASR.APIResponse> responses = new LinkedBlockingQueue<>();
		//handle down stream
		threadService.execute(() -> handleDownStream(config.getDownUrl() + "?id=" + id, responses));
		return responses;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc as server to sent up stream
	 */
	private void handleUpStream(String url, final RequestBody body) {
		Headers headers = buildUpHeader();
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
				while (sink.size() > 0) {
					long len = sink.readIntLe();
					/*if (len > byteCount) {
						log.error("Resolve sink failed, error msg: {}", sink.toString());
						responses.addAll(buildFailResponse(sink.toString()));
						return;
					}*/
					byte[] data = sink.readByteArray(len);
					responses.add(ASR.APIResponse.parseFrom(data));
				}
			} catch (Exception e) {
				log.error("Read response failed. error msg: ", e);
			}
		});
		return;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc send all requests by POST method
	 */
	private void handlePostASR(String url, RequestBody body, Headers headers, BiConsumer<Buffer, Long> callBack) {

		if (null != callBack) {
			clientBuilder.addNetworkInterceptor(chain -> {
				Response response = chain.proceed(chain.request());
				log.info("resp_body: {}", response.body().getClass());
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
				//trigger read response
				response.body().string();
			}
		});
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc parse ASR result to NLU
	 */
	public ASR.APIResponse resolveASR2NLU(ASR.APIResponse response) {

		if (null != response && 0 == response.errNo) {
			StringBuilder sb      = new StringBuilder();
			String[]      words   = response.result.word;
			String[]      unknown = response.result.uncertainWord;
			if (0 == unknown.length) {
				for (String word : words) {
					sb.append(word);
				}
				String result = sb.toString();
				log.info("Result Package：{}", result);
				String nlu = nluService.dialog("device123", "uid123", result);
				if (null != nlu) {
					response.result.word = new String[]{nlu};
				}
				return response;
			}
		}
		return null;
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
		return responses;
	}
}
