package com.ecarx.asrapi.interfaces.domain;

import lombok.Data;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author ITACHY
 * @date 2018/11/6
 * @desc TO-DO
 */

@Data
public class NLUResponse implements Callback {

	private static Logger log = LoggerFactory.getLogger(NLUResponse.class);

	private String result;

	private String errMsg;

	/**
	 * Called when the request could not be executed due to cancellation, a connectivity problem or
	 * timeout. Because networks can fail during an exchange, it is possible that the remote server
	 * accepted the request before the failure.
	 * @param call
	 * @param e
	 */
	@Override
	public void onFailure(Call call, IOException e) {
		log.error("NLU request failed, and error msg: ", e);
		errMsg = e.getMessage();
	}

	/**
	 * Called when the HTTP response was successfully returned by the remote server. The callback may
	 * proceed to read the response body with {@link Response#body}. The response is still live until
	 * its response body is {@linkplain ResponseBody closed}. The recipient of the callback may
	 * consume the response body on another thread.
	 *
	 * <p>Note that transport-layer success (receiving a HTTP response code, headers and body) does
	 * not necessarily indicate application-layer success: {@code response} may still indicate an
	 * unhappy HTTP response code like 404 or 500.
	 * @param call
	 * @param response
	 */
	@Override
	public void onResponse(Call call, Response response) throws IOException {
		result = response.body().string();
		log.info("NLU request success, and response: {}", result);
	}
}
