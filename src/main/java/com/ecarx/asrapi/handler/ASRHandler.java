package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.google.protobuf.nano.MessageNano;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc ASR Web 请求处理
 */

@RestController
public class ASRHandler {

	private static Logger log = LoggerFactory.getLogger(ASRHandler.class);

	@Resource
	private HttpService httpService;

	@GetMapping("hello")
	public String hello() {
		return "Hello ASR!";
	}

	//@PostMapping("auth")
	@GetMapping("auth")
	public Mono<ServerResponse> handleAuth() {
		return handleNLUResponse(httpService.actvAuth(null));
	}

	//@PostMapping("auth")
	@GetMapping("audth")
	public Mono<ServerResponse> handleAuthWithAudio() throws Exception {
		return handleNLUResponse(httpService.actvAuthWithAudio(constructParam()));
	}

	//@PostMapping("asr")
	@GetMapping("asr")
	public Mono<ServerResponse> handleASR() throws Exception {
		return handleNLUResponse(httpService.handleASR(constructParam()));
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc Handle NUL Result
	 */
	private Mono<ServerResponse> handleNLUResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {
		Buffer buffer = new Buffer();
		try {
			ASR.APIResponse response = responses.take();
			while (null != response) {
				response = resolveASRResponse(response);
				buffer.writeIntLe(response.getSerializedSize());
				buffer.write(MessageNano.toByteArray(response));
				buffer.flush();
				if (ASR.API_RESP_TYPE_LAST == response.type) {
					return ServerResponse.ok().body(BodyInserters.fromObject(buffer));
				}
				response = responses.take();
			}
			return ServerResponse.ok().body(BodyInserters.fromObject(buffer));
		} catch (Exception e) {
			log.error("Take method of LinkedBlockingQueue class occur InterruptedException, error msg:", e);
			return ServerResponse.ok().body(BodyInserters.fromObject(buffer));
		}
	}

	private byte[] constructParam() throws Exception {
		FileInputStream fis = new FileInputStream("D:\\common\\projects\\ecarx\\java\\asr-api\\src\\main\\resources\\weather.pcm");

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
			log.info("Third Package：{}", new String(data));
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
