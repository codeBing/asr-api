package com.ecarx.asrapi.service;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.consts.EnvConsts;
import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.interfaces.domain.ASRResponse;
import com.ecarx.asrapi.interfaces.ResponseCallBack;
import com.google.protobuf.nano.MessageNano;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc Http request service
 */
@Service
public class HttpService {

	private static final Logger log = LoggerFactory.getLogger(HttpService.class);

	@Resource
	private ASRConfig config;

	@Resource
	private ParamService paramService;

	@Resource
	private NLUService nluService;

	private ThreadPoolExecutor executor;

	private OkHttpClient httpClient;

	private OkHttpClient.Builder clientBuilder;

	public HttpService() {
		this.clientBuilder = new OkHttpClient.Builder();
		this.clientBuilder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
		httpClient = clientBuilder.build();

		this.executor = new ScheduledThreadPoolExecutor(500);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc activate asr
	 */
	public LinkedBlockingQueue<ASR.APIResponse> actvAuth(byte[] data) {
		return handleHttp2(EnvConsts.ASR_AUTH, data);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc activate asr and handle asr request
	 */
	public LinkedBlockingQueue<ASR.APIResponse> actvAuthWithAudio(byte[] data) {
		return handleHttp2(EnvConsts.ASR_AUTH_WITH_AUDIO, data);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc handle asr request
	 */
	public LinkedBlockingQueue<ASR.APIResponse> handleASR(byte[] data) {
		return handleHttp2(EnvConsts.ASR_DATA, data);
	}

	//统一处理Http2
	private LinkedBlockingQueue<ASR.APIResponse> handleHttp2(String type, byte[] data) {

		String id = UUID.randomUUID().toString();

		LinkedBlockingQueue<ASR.APIResponse> responses = new LinkedBlockingQueue<>();
		//handle up steam
		executor.execute(() -> handleUpStream(config.getUpUrl() + "?id=" + id, type, data));
		//handle down stream
		executor.execute(() -> handleDownStream(config.getDownUrl() + "?id=" + id, responses));
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
		postASR(url, body, headers, null);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc handle down stream
	 */
	private void handleDownStream(String url, LinkedBlockingQueue<ASR.APIResponse> responses) {

		Headers          headers = buildDownHeader();
		FormBody.Builder builder = new FormBody.Builder();
		builder.add("test_id", "");
		try {
			TimeUnit.MILLISECONDS.sleep(1000);
		} catch (Exception e) {
			log.error("Error log:", e);
		}
		postASR(url, builder.build(), headers, (sink, byteCount) -> {
			try {
				if (sink.toString().indexOf("Request") > 0) {
					log.error("Invalid Request!");
					responses.addAll(buildFailResponse(sink.toString()));
					return;
				}
				if (sink.toString().indexOf("OK") > 0) {
					responses.addAll(buildFailResponse(sink.toString()));
					return;
				}
				//log.info("sink msg: {}", sink.toString());
				while (sink.size() > 0) {
					long   len  = sink.readIntLe();
					byte[] data = sink.readByteArray(len);
					responses.add(ASR.APIResponse.parseFrom(data));
				}
			} catch (Exception e) {
				log.error("Read response failed. error msg: ", e);
			}
		});
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc send all requests by POST method
	 */
	private void postASR(String url, RequestBody body, Headers headers, ResponseCallBack callBack) {

		if (null != callBack) {
			clientBuilder.addNetworkInterceptor(chain -> {
				Response response = chain.proceed(chain.request());
				return response.newBuilder().body(new ASRResponse(response.body(), callBack)).build();
			});
			httpClient = clientBuilder.build();
		}

		Request.Builder requestBuilder = new Request.Builder();
		requestBuilder.url(url);
		if (null != body) {
			requestBuilder.post(body);
		}
		if (null != headers) {
			requestBuilder.headers(headers);
		}
		Request request = requestBuilder.build();
		Call    call    = httpClient.newCall(request);

		/*call.enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("ASR request failed, fail msg: ", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				log.info(url + ", resp_code: {}", response.code());
				log.info(url + ", resp_message: {}", response.message());
				log.info(url + ", resp_Transfer-Encoding: {}", response.header("Transfer-Encoding"));
				log.info(url + ", resp_protocol: {}", response.protocol());
				log.info(url + ", resp_body: {}", response.body().string());
			}
		});*/
		try {
			Response response = httpClient.newCall(request).execute();
			log.info(url + ", resp_code: {}", response.code());
			log.info(url + ", resp_message: {}", response.message());
			log.info(url + ", resp_Transfer-Encoding: {}", response.header("Transfer-Encoding"));
			log.info(url + ", resp_protocol: {}", response.protocol());
			log.info(url + ", resp_body: {}", response.body().string());
		} catch (Exception e) {
			log.error("Http2 post  failed: ", e);
		}
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc parse ASR result to NLU
	 */
	public ASR.APIResponse resolveASR2NLU(ASR.APIResponse response) {

		StringBuilder sb      = new StringBuilder();
		String[]      words   = response.result.word;
		String[]      unknown = response.result.uncertainWord;
		if (0 == unknown.length) {
			for (String word : words) {
				sb.append(word);
			}
			log.info("Result Package：{}", sb.toString());
			String nlu = nluService.dialog("device123", "uid123", sb.toString());
			if (null != nlu) {
				response.result.word = new String[]{nlu};
			}
			return response;
		}
		return response;
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
		Set<String>     headerSet     = headers.keySet();
		for (String key : headerSet) {
			headerBiulder.add(key, headers.get(key));
		}
		return headerBiulder.build();
	}

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
