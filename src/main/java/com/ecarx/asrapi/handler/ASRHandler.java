package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.ecarx.asrapi.service.NLUService;
import com.google.protobuf.nano.MessageNano;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okio.Buffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

	private final ThreadPoolExecutor executor;

	@Autowired
	public ASRHandler(final ASRConfig config, final NLUService nluService, final HttpService httpService) {

		this.config = config;
		this.nluService = nluService;
		this.httpService = httpService;

		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc for client connection test， need to delete
	 */
	@PostMapping(value = "hello")
	@ResponseBody
	public Mono<String> hello() {
		return Mono.just("Hello ASR");
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc response client up request
	 */
	@PostMapping(value = "up", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	@ResponseBody
	public Mono<String> handleASRUp(@RequestParam String id, ServerHttpRequest request) {

		long                                startTime = System.currentTimeMillis();
		LinkedBlockingQueue<ASR.APIRequest> requests  = new LinkedBlockingQueue<>();

		return Mono.create(call ->

				request.getBody().subscribe(buffer -> {
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
							log.info("receive msg type: {}, 耗时： {}", apiRequest.apiReqType,
									System.currentTimeMillis() - startTime);
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
	 * @date 2018/11/8
	 * @desc response client down request
	 */
	@PostMapping(value = "down", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Mono<Void> handleASRDown(@RequestParam String id, ServerHttpResponse response) {

		LinkedBlockingQueue<ASR.APIResponse> responses = httpService
				.handleASRDown(id, new FormBody.Builder().build());

		Flux<ASR.APIResponse> responze = buildASRResponse(responses);

		/*return response.writeWith(Flux.fromStream(responze.toStream()
				.map(responz -> {
					log.info("响应数据： {}", responz.toString());
					Buffer buffer = new Buffer();
					buffer.writeIntLe(responz.getSerializedSize());
					buffer.write(MessageNano.toByteArray(responz));
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					try {
						buffer.writeTo(os);
					} catch (IOException e) {
						e.printStackTrace();
					}
					DataBuffer dataBuffer = new DefaultDataBufferFactory().allocateBuffer();
					dataBuffer.write(os.toByteArray());
					return dataBuffer;
				})));*/

		return response.writeAndFlushWith(Flux.just(Flux.fromStream(responze.toStream()
				.map(responz -> {
					log.info("Web response type: {}", responz.type);
					Buffer buffer = new Buffer();
					buffer.writeIntLe(responz.getSerializedSize());
					buffer.write(MessageNano.toByteArray(responz));
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					try {
						buffer.writeTo(os);
					} catch (IOException e) {
						log.error("Web response error, error msg: ", e);
					}
					DataBuffer dataBuffer = new DefaultDataBufferFactory().allocateBuffer();
					dataBuffer.write(os.toByteArray());
					return dataBuffer;
				}))));
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc Handle NUL Result
	 */
	private String handleNLUResponse(final ASR.APIResponse response) {

		if (0 == response.errNo) {
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
				log.info("ASR result：{}", text);
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
	private Flux<ASR.APIResponse> buildASRResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {

		int     timeout  = config.getTimeout();
		Boolean finished = false;

		List<ASR.APIResponse> apiResponses = new ArrayList<>();
		try {
			Future<String>  future   = null;
			ASR.APIResponse response = responses.poll(timeout, TimeUnit.MILLISECONDS);
			while (null != response) {
				final int type = response.type;
				if (ASR.API_RESP_TYPE_THIRD == type || ASR.API_RESP_TYPE_HEART == type) {
					response = responses.poll(timeout, TimeUnit.MILLISECONDS);
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
				apiResponses.add(response);
				if (finished) {
					break;
				}
				response = responses.poll(timeout, TimeUnit.MILLISECONDS);
			}
		} catch (Exception e) {
			log.error("Take response failed, detail error msg: ", e);
		}
		return Flux.fromIterable(apiResponses);
	}
}
