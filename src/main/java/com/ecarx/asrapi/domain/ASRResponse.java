package com.ecarx.asrapi.domain;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * @author ITACHY
 * @date 2018/11/1
 * @desc customize asr response
 */
public class ASRResponse extends ResponseBody {

	private ResponseBody             respBody;
	private BiConsumer<Buffer, Long> callBack;

	private BufferedSource buffSource = null;

	public ASRResponse(ResponseBody body, BiConsumer<Buffer, Long> callBack) {
		this.respBody = body;
		this.callBack = callBack;
	}

	@Override
	public long contentLength() {
		return respBody.contentLength();
	}

	@Override
	public MediaType contentType() {
		return respBody.contentType();
	}

	@Override
	public BufferedSource source() {
		if (buffSource == null) {
			buffSource = Okio.buffer(source(respBody.source()));
		}
		return buffSource;
	}

	private Source source(Source source) {
		return new ForwardingSource(source) {
			@Override
			public long read(Buffer sink, long byteCount) throws IOException {
				long bytes = super.read(sink, byteCount);
				if (callBack != null) {
					callBack.accept(sink, byteCount);
				}
				return bytes;
			}
		};
	}

}
