package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.ecarx.asrapi.service.NLUService;
import com.ecarx.asrapi.service.WebService;
import com.google.protobuf.nano.MessageNano;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okio.Buffer;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc asr steam format
 */

@Slf4j
@Controller
@RequestMapping("/asr")
public class ASRHandler {

	private final ASRConfig config;

	private final NLUService nluService;

	private final HttpService httpService;

	private final WebService webService;

	private final ThreadPoolExecutor executor;

	@Autowired
	public ASRHandler(final ASRConfig config, final NLUService nluService, final HttpService httpService,
			final WebService webService) {

		this.config = config;
		this.nluService = nluService;
		this.webService = webService;
		this.httpService = httpService;

		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc for client connection test
	 */
	@PostMapping(value = "hello")
	@ResponseBody
	public Mono<String> hello() {
		return Mono.just("Hello ASR");
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc for connecting ARS
	 */
	@GetMapping(value = "", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Flux<okhttp3.ResponseBody> handleASR() throws Exception {
		LinkedBlockingQueue<ASR.APIResponse> responses = webService.handleASR("local", constructParam());
		return buildASRResponse(responses);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc response client up request
	 */
	@PostMapping(value = "up")
	@ResponseBody
	public Mono<String> handleASRUp(@RequestParam String id, ServerHttpRequest request) {

		log.info("收到请求，id={}", id);
		long                                startTime = System.currentTimeMillis();
		LinkedBlockingQueue<ASR.APIRequest> requests  = new LinkedBlockingQueue<>();

		request.getBody().doOnNext(buffer -> log.info("收到数据"))
				.subscribe(buffer -> {
					int length = buffer.readableByteCount();
					if (length > 4) {
						byte[] bytes = new byte[length];
						byte[] data  = new byte[length - 4];
						buffer.read(bytes);
						System.arraycopy(bytes, 4, data, 0, data.length);
						try {
							ASR.APIRequest apiRequest = ASR.APIRequest.parseFrom(data);
							log.info("receive msg type: {}", apiRequest.apiReqType);
							log.info("耗时: {}", System.currentTimeMillis() - startTime);
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				});
		/*List<ASR.APIRequest> requestList = request.getBody().toStream().parallel().map(buffer -> {
			ASR.APIRequest apirequest = null;
			int            length     = buffer.readableByteCount();
			if (length > 4) {
				byte[] bytes = new byte[length];
				byte[] data  = new byte[length - 4];
				buffer.read(bytes);
				System.arraycopy(bytes, 4, data, 0, data.length);
				try {
					apirequest = ASR.APIRequest.parseFrom(data);
					log.info("消耗时间：{}", System.currentTimeMillis() - startTime);
					log.info("Request Msg: {}", apirequest.apiReqType);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return apirequest;
		}).collect(Collectors.toList());*/
		//httpService.handleASRUp(id, requests);
		try {
			TimeUnit.SECONDS.sleep(5);
		} catch (Exception e) {

		}

		try {
			TimeUnit.SECONDS.sleep(5);
		} catch (Exception e) {

		}

		try {
			TimeUnit.SECONDS.sleep(5);
		} catch (Exception e) {

		}
		return Mono.just("OK!");

	}

	/**
	 * @author ITACHY
	 * @date 2018/11/8
	 * @desc response client down request
	 */
	@PostMapping(value = "down", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Flux<okhttp3.ResponseBody> handleASRDown(@RequestBody String id) {
		LinkedBlockingQueue<ASR.APIResponse> responses = httpService.handleASRDown(id, new FormBody.Builder().build());
		return buildASRResponse(responses);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc Handle NUL Result
	 */
	private String handleNLUResponse(final ASR.APIResponse response) {

		if (null != response && 0 == response.errNo) {
			StringBuilder sb      = new StringBuilder();
			String[]      words   = response.result.word;
			String[]      unknown = response.result.uncertainWord;
			if (0 == unknown.length) {
				for (String word : words) {
					sb.append(word);
				}
				String uid    = response.id;
				String device = response.id;
				String text   = sb.toString();
				log.info("Result Package：{}", text);
				return nluService.dialog(device, uid, text);
			}
		}
		return null;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/12
	 * @desc 构建ASR 返回结果
	 */
	private Flux<okhttp3.ResponseBody> buildASRResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {
		Buffer                buffer       = null;
		Boolean               finished     = true;
		List<ASR.APIResponse> apiResponses = new ArrayList<>();
		try {
			Future<String>  future   = null;
			ASR.APIResponse response = responses.poll(30000, TimeUnit.MILLISECONDS);
			while (null != response) {
				final int type = response.type;
				if (ASR.API_RESP_TYPE_THIRD == type || ASR.API_RESP_TYPE_HEART == type) {
					response = responses.poll(30000, TimeUnit.MILLISECONDS);
					continue;
				}

				if (ASR.API_RESP_TYPE_RES == type) {
					final ASR.APIResponse asrRespinse = response;
					future = executor.submit(() -> handleNLUResponse(asrRespinse));
				}

				if (ASR.API_RESP_TYPE_LAST == type) {
					if (null != future) {
						String nlu = future.get();
						if (null != nlu) {
							ASR.ASRResult result = new ASR.ASRResult();
							result.word = new String[]{nlu};
							response.result = result;
						}
					}
					finished = true;
				}
				/*buffer = new Buffer();
				buffer.writeIntLe(response.getSerializedSize());
				buffer.write(MessageNano.toByteArray(response));
				buffer.flush();*/
				apiResponses.add(response);
				if (finished) {
					break;
				}
				response = responses.poll(30000, TimeUnit.MILLISECONDS);
			}
		} catch (Exception e) {
			log.error("Take response failed, detail error msg: ", e);
		}

		List<okhttp3.ResponseBody> collect = apiResponses.stream().map(response -> new okhttp3.ResponseBody() {
			@Nullable
			@Override
			public okhttp3.MediaType contentType() {
				return null;
			}

			@Override
			public long contentLength() {
				return response.getSerializedSize();
			}

			@Override
			public BufferedSource source() {
				Buffer buffer = new Buffer();
				buffer.writeIntLe(response.getSerializedSize());
				buffer.write(MessageNano.toByteArray(response));
				buffer.flush();
				return buffer;
			}
		}).collect(Collectors.toList());
		return Flux.fromIterable(collect);
	}

	private byte[] constructParam() throws Exception {

		String filePath = "D:\\common\\projects\\ecarx\\java\\asr-api\\src\\main\\resources\\weather.pcm";

		int                   length;
		byte[]                buffer = new byte[5120];
		FileInputStream       fis    = new FileInputStream(filePath);
		ByteArrayOutputStream baos   = new ByteArrayOutputStream();

		while ((length = fis.read(buffer)) != -1) {
			baos.write(buffer, 0, length);
		}
		byte[] data = baos.toByteArray();
		log.info("data length: {}", data.length);
		//data = "hello world".getBytes(Charset.defaultCharset());
		return data;

	}
}
