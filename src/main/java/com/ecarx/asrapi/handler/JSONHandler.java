package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.ecarx.asrapi.service.NLUService;
import com.ecarx.asrapi.service.ThreadService;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc ASR Web 请求处理
 */
@RestController
@RequestMapping("/json")
public class JSONHandler {

	private static Logger log = LoggerFactory.getLogger(JSONHandler.class);

	private final NLUService nluService;

	private final HttpService httpService;

	private final ThreadService threadService;

	@Autowired
	public JSONHandler(final NLUService nluService, final HttpService httpService, final ThreadService threadService) {
		this.nluService = nluService;
		this.httpService = httpService;
		this.threadService = threadService;

	}

	@GetMapping("/hello")
	public Mono<String> hello() {
		return Mono.just("Hello ASR!");
	}

	@GetMapping("/asr")
	public Flux<ASR.APIResponse> handleASR() throws Exception {
		return handleNLUResponse(httpService.handleASR(constructParam()));
	}

	//@PostMapping("asr")
	@GetMapping("/asr")
	public Mono<String> handleASRUp(ServerRequest request) {
		String      id   = request.queryParam("id").get();
		RequestBody body = request.bodyToMono(RequestBody.class).block();
		httpService.handleASRUp(id, body);
		return Mono.just("OK");
	}

	//@PostMapping("asr")
	@GetMapping("/asr")
	public Flux<ASR.APIResponse> handleASRDown(ServerRequest request) {
		String      id   = request.queryParam("id").get();
		RequestBody body = request.bodyToMono(RequestBody.class).block();
		return handleNLUResponse(httpService.handleASRDown(id, body));
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc Handle NUL Result
	 */
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
							future = (Future<String>) threadService.submit(() -> nluService.dialog(device, uid, text));
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
		return Flux.fromIterable(apiResponses.stream()
				.map(response -> resolveASRResponse(response))
				.collect(Collectors.toList()));
	}

	private byte[] constructParam() throws Exception {

		String filePath = "D:\\common\\projects\\ecarx\\java\\asr-api\\src\\main\\resources\\weather.pcm";

		FileInputStream fis = new FileInputStream(filePath);

		byte[] buffer = new byte[5120];

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		int length = 0;
		while ((length = fis.read(buffer)) != -1) {
			baos.write(buffer, 0, length);
		}
		byte[] data = baos.toByteArray();
		log.info("data length: {}", data.length);
		return data;

	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc resolve ASR
	 */
	private ASR.APIResponse resolveASRResponse(ASR.APIResponse response) {

		switch (response.type) {
		case ASR.API_RESP_TYPE_MIDDLE:
		case ASR.API_RESP_TYPE_THIRD:
		case ASR.API_RESP_TYPE_HEART:
		case ASR.API_RESP_TYPE_LAST:
			return logASRResult(response);
		case ASR.API_RESP_TYPE_RES:
			return httpService.resolveASR2NLU(response);
		default:
			return response;
		}

	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc log ASR result
	 */
	private ASR.APIResponse logASRResult(ASR.APIResponse response) {
		switch (response.type) {
		case ASR.API_RESP_TYPE_MIDDLE:
			StringBuilder sb = new StringBuilder();
			String[] words = response.result.word;
			String[] unknown = response.result.uncertainWord;
			if (0 == unknown.length) {
				for (String word : words) {
					sb.append(word);
				}
				log.info("Middle Package：{}", sb.toString());
			}
			break;
		case ASR.API_RESP_TYPE_THIRD:
			byte[] data = response.thirdData.thirdData;
			log.info("Third-Data Package：{}", new String(data));
			break;
		case ASR.API_RESP_TYPE_HEART:
			log.info("Heart Package!");
			break;
		case ASR.API_RESP_TYPE_LAST:
			log.info("Last Package!");
			break;

		}
		return response;
	}
}
