package com.ecarx.asrapi.handler;

import com.ecarx.asrapi.dto.nano.ASR;
import com.ecarx.asrapi.service.HttpService;
import com.google.protobuf.nano.MessageNano;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author ITACHY
 * @date 2018/10/29
 * @desc ASR Web 请求处理
 */

@RestController
public class ASRHandler {

	private static Logger log = LoggerFactory.getLogger(ASRHandler.class);

	private final HttpService httpService;

	@Autowired
	public ASRHandler(final HttpService httpService) {
		this.httpService = httpService;
	}

	@GetMapping("hello")
	public Mono<String> hello() {
		return Mono.just("Hello ASR!");
	}

	//@PostMapping("auth")
	@GetMapping("auth")
	public Flux<ASR.APIResponse> handleAuth() {
		return null; //handleNLUResponse(httpService.actvAuth(null));
	}

	//@PostMapping("audth")
	@GetMapping("audth")
	public Flux<ResponseBody> handleAuthWithAudio() throws Exception {
		return handleNLUResponse(httpService.actvAuthWithAudio(constructParam()));
	}

	//@PostMapping("asr")
	@GetMapping("asr")
	public Flux<ResponseBody> handleASR() throws Exception {
		return handleNLUResponse(httpService.handleASR(constructParam()));
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/5
	 * @desc Handle NUL Result
	 */
	private Flux<ResponseBody> handleNLUResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {

		Buffer             buffer       = new Buffer();
		List<ResponseBody> apiResponses = new ArrayList<>();
		try {
			ASR.APIResponse response = responses.take();
			while (null != response) {
				response = resolveASRResponse(response);
				buffer.writeIntLe(response.getSerializedSize());
				buffer.write(MessageNano.toByteArray(response));
				buffer.flush();
				apiResponses.add(ResponseBody.create(null, response.getSerializedSize(), buffer));
				if (ASR.API_RESP_TYPE_LAST == response.type) {
					return Flux.fromIterable(apiResponses);
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
