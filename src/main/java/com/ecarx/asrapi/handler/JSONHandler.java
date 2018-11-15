package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.ecarx.asrapi.service.NLUService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

	private final HttpService httpService;

	private final ThreadPoolExecutor executor;

	public JSONHandler(final ASRConfig config, final NLUService nluService, final HttpService httpService) {

		this.nluService = nluService;
		this.httpService = httpService;

		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());

	}

	/**
	 * @author ITACHY
	 * @date 2018/11/15
	 * @desc for client connection test， need to delete
	 */
	@GetMapping(value = "hello")
	public Mono<String> hello() {
		return Mono.just("Hello ASR!");
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/15
	 * @desc provide json support
	 */
	@PostMapping(value = "up", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<String> handleASRUp(@RequestParam String id, ServerHttpRequest request) {

		long                                startTime = System.currentTimeMillis();
		LinkedBlockingQueue<ASR.APIRequest> requests  = new LinkedBlockingQueue<>();

		return Mono.create(call -> request.getBody().subscribe(buffer -> {
					boolean        flag       = false;
					ASR.APIRequest apiRequest = null;
					int            length     = buffer.readableByteCount();
					if (length > 4) {
						byte[] bytes = new byte[length];
						byte[] data  = new byte[length - 4];
						buffer.read(bytes);
						System.arraycopy(bytes, 4, data, 0, data.length);
						try {
							apiRequest = ASR.APIRequest.parseFrom(data);
							if (ASR.API_REQ_TYPE_LAST == apiRequest.apiReqType) {
								flag = true;
							}
							requests.add(apiRequest);
							log.info("receive msg type: {}", apiRequest.apiReqType);
							log.info("耗时: {}", System.currentTimeMillis() - startTime);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					if (flag) {
						httpService.handleASRUp(id, requests);
						call.success("ok");
					}
				})
		);
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/15
	 * @desc provide json support
	 */
	@PostMapping(value = "down", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ASR.APIResponse> handleASRDown(@RequestParam String id) {
		return handleASRResponse(httpService.handleASRDown(id, new FormBody.Builder().build()));
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
					if (null != future) {
						String        nlu    = future.get();
						ASR.ASRResult result = new ASR.ASRResult();
						if (null != nlu) {
							result.word = new String[]{nlu};
							response.result = result;
						}
					}
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
}
