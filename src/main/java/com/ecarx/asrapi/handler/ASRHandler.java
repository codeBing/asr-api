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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

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
	public Mono<ServerResponse> handleASR() throws Exception {
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

		Flux<DataBuffer> fluxBody = request.getBody();

		/*fluxBody.subscribe(buffer -> {
			byte[] bytes = new byte[buffer.readableByteCount()];
			buffer.read(bytes);
			DataBufferUtils.release(buffer);
			try {
				String bodyString = new String(bytes, "utf-8");
				System.out.println(bodyString);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		});*/
		fluxBody.subscribe(body -> {
			byte[] lenByte = new byte[4];
			body.read(lenByte);
			try {

				int    len   = bytesToInt(lenByte, 0);
				byte[] intLe = new byte[len];
				int    i     = 0;
				//while (i < len) {
				body.read(intLe, i, len);
				//}

				//ByteArrayOutputStream baos       = new ByteArrayOutputStream();
				ASR.APIRequest apiRequest = ASR.APIRequest.parseFrom(intLe);
				System.out.println(apiRequest.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		//okhttp3.RequestBody body = okhttp3.RequestBody.create(null, data);
		//httpService.handleASRUp(id, body);
		return Mono.just("OK");
	}

	public static int bytesToInt(byte[] src, int offset) {
		int value;
		value = (int) ((src[offset] & 0xFF)
				| ((src[offset + 1] & 0xFF) << 8)
				| ((src[offset + 2] & 0xFF) << 16)
				| ((src[offset + 3] & 0xFF) << 24));
		return value;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/8
	 * @desc response client down request
	 */
	@PostMapping(value = "down")
	public Mono<ServerResponse> handleASRDown(@RequestBody String id) {

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
	private Mono<ServerResponse> buildASRResponse(LinkedBlockingQueue<ASR.APIResponse> responses) {
		Buffer buffer = new Buffer();
		try {
			Future<String>  future   = null;
			ASR.APIResponse response = responses.take();
			while (null != response) {
				final int type = response.type;
				if (ASR.API_RESP_TYPE_THIRD == type || ASR.API_RESP_TYPE_HEART == type) {
					response = responses.take();
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
				}
				buffer.writeIntLe(response.getSerializedSize());
				buffer.write(MessageNano.toByteArray(response));
				buffer.flush();
				response = responses.take();
			}
		} catch (Exception e) {
			log.error("Take response failed, detail error msg: ", e);
			return ServerResponse.badRequest().body(BodyInserters.fromObject(e));
		}
		return ServerResponse.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(BodyInserters.fromObject(BodyInserters.fromObject(buffer)));
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
