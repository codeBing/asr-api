package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.ecarx.asrapi.service.NLUService;
import com.ecarx.asrapi.service.WebService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@RestController
@RequestMapping("/json")
public class JSONHandler {

	private final NLUService nluService;

	private final WebService webService;

	private final HttpService httpService;

	private final ThreadPoolExecutor executor;

	@Autowired
	public JSONHandler(final ASRConfig config, final NLUService nluService, final HttpService httpService,
			final WebService webService) {
		this.nluService = nluService;
		this.webService = webService;
		this.httpService = httpService;

		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());

	}

	@GetMapping(value = "hello", produces = MediaType.TEXT_PLAIN_VALUE)
	public Mono<String> hello() {
		return Mono.just("Hello ASR!");
	}

	@GetMapping(value = "", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ASR.APIResponse> handleASR() throws Exception {
		return handleNLUResponse(webService.handleASR("", constructParam()));
	}

	@PostMapping(value = "up", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<String> handleASRUp(@RequestParam String id, ServerHttpRequest request) {

		byte[] data = request.getBody().toIterable().iterator().next().asByteBuffer().array();

		okhttp3.RequestBody body = okhttp3.RequestBody.create(null, data);
		httpService.handleASRUp(id, body);
		return Mono.just("OK");
	}

	@PostMapping(value = "down", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ASR.APIResponse> handleASRDown(@RequestParam String id, ServerHttpRequest request) {
		byte[]              data = request.getBody().toIterable().iterator().next().asByteBuffer().array();
		okhttp3.RequestBody body = okhttp3.RequestBody.create(null, data);
		return handleASRResponse(httpService.handleASRDown(id, body));
	}

	private Flux<ASR.APIResponse> handleNLUResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {
		Future<String>        future       = null;
		Boolean               finished     = false;
		List<ASR.APIResponse> apiResponses = new ArrayList<>();
		try {
			ASR.APIResponse response = responses.take();
			while (null != response) {
				int type = response.type;
				if (ASR.API_RESP_TYPE_THIRD == type || ASR.API_RESP_TYPE_HEART == type) {
					response = responses.take();
					continue;
				} else if (ASR.API_RESP_TYPE_RES == type) {

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
							future = executor.submit(() -> nluService.dialog(device, uid, text));
						}
					}
				} else if (ASR.API_RESP_TYPE_LAST == type) {
					String        nlu    = future.get();
					ASR.ASRResult result = new ASR.ASRResult();
					result.word = new String[]{nlu};
					response.result = result;
					finished = true;
				}
				apiResponses.add(response);
				if (finished) {
					break;
				}
				response = responses.take();
			}
		} catch (Exception e) {
			log.error("Take response occur eror. error msg: {}", e);
		}
		return Flux.fromIterable(apiResponses);
	}

	private Flux<ASR.APIResponse> handleASRResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {
		Future<String>        future       = null;
		Boolean               finished     = false;
		List<ASR.APIResponse> apiResponses = new ArrayList<>();
		try {
			ASR.APIResponse response = responses.take();
			while (null != response) {
				int type = response.type;
				if (ASR.API_RESP_TYPE_THIRD == type || ASR.API_RESP_TYPE_HEART == type) {
					response = responses.take();
					continue;
				} else if (ASR.API_RESP_TYPE_RES == type) {

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
							future = executor.submit(() -> nluService.dialog(device, uid, text));
						}
					}
				} else if (ASR.API_RESP_TYPE_LAST == type) {
					String        nlu    = future.get();
					ASR.ASRResult result = new ASR.ASRResult();
					result.word = new String[]{nlu};
					response.result = result;
					finished = true;
				}
				apiResponses.add(response);
				if (finished) {
					break;
				}
				response = responses.take();
			}

		} catch (Exception e) {
			log.error("Take response occur eror. error msg: {}", e);
		}
		return Flux.fromIterable(apiResponses);
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
		return data;

	}
}
