package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.ecarx.asrapi.service.NLUService;
import com.ecarx.asrapi.service.ThreadService;
import com.google.protobuf.nano.MessageNano;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc ASR Web 请求处理
 */

@Controller
public class ASRHandler {

	private static Logger log = LoggerFactory.getLogger(ASRHandler.class);

	private final NLUService nluService;

	private final HttpService httpService;

	private final ThreadService threadService;

	@Autowired
	public ASRHandler(final NLUService nluService, final HttpService httpService, final ThreadService threadService) {
		this.nluService = nluService;
		this.httpService = httpService;
		this.threadService = threadService;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc for client connection test
	 */
	@GetMapping("hello")
	@org.springframework.web.bind.annotation.ResponseBody
	public Mono<String> hello() {
		return Mono.just("Hello ASR!");
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc for connecting ARS
	 */
	@GetMapping("asr")
	public Mono<ServerResponse> handleASR() throws Exception {
		Mono<ResponseBody> resBody = handleNLUResponse(httpService.handleASR(constructParam()));
		return Mono.just(ServerResponse.ok().body(BodyInserters.fromObject(resBody)).block());
	}

	/*@PostMapping("auth/up")
	public Mono<String> handleAuthUp(ServerRequest serverRequest) {

		String      id   = serverRequest.queryParam("id").get();
		RequestBody body = serverRequest.bodyToMono(RequestBody.class).block();
		httpService.handleASRUp(id, body);
		return Mono.just("OK");
	}

	@PostMapping("auth/down")
	public Mono<ResponseBody> handleAuthDown(ServerRequest serverRequest) {
		String      id   = serverRequest.queryParam("id").get();
		RequestBody body = serverRequest.bodyToMono(RequestBody.class).block();
		return handleNLUResponse(httpService.handleASRDown(id, body));
	}*/

	/**
	 * @author ITACHY
	 * @date 2018/11/7
	 * @desc response client request and return ASR/NLU response
	 */
	@PostMapping("asr/up")
	public Mono<String> handleASRUp(ServerRequest serverRequest) {
		String      id   = serverRequest.queryParam("id").get();
		RequestBody body = serverRequest.bodyToMono(RequestBody.class).block();
		httpService.handleASRUp(id, body);
		return Mono.just("OK");
	}

	@PostMapping("asr/down")
	public Mono<ServerResponse> handleASRDown(ServerRequest request) {
		String             id      = request.queryParam("id").get();
		RequestBody        body    = request.bodyToMono(RequestBody.class).block();
		Mono<ResponseBody> resBody = handleNLUResponse(httpService.handleASRDown(id, body));
		return Mono.just(ServerResponse.ok().body(BodyInserters.fromObject(resBody)).block());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc Handle NUL Result
	 */
	private Mono<ResponseBody> handleNLUResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {

		Future<String> future   = null;
		Boolean        finished = false;
		Buffer         buffer   = new Buffer();
		try {
			ASR.APIResponse response = responses.take();
			while (null != response) {
				//response = resolveASRResponse(response);
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
				buffer.writeIntLe(response.getSerializedSize());
				buffer.write(MessageNano.toByteArray(response));
				buffer.flush();
				if (finished) {
					return Mono.just(ResponseBody.create(null, buffer.size(), buffer));
				}
				response = responses.take();
			}
		} catch (Exception e) {
			log.error("Take method of LinkedBlockingQueue class occur InterruptedException, error msg:", e);
		}
		return null;
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
