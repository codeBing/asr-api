package com.ecarx.asrapi.interfaces.domain;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * @author ITACHY
 * @date 2018/11/6
 * @desc TO-DO
 */

public class NLUResponse extends ResponseBody {

	private static Logger log = LoggerFactory.getLogger(NLUResponse.class);

	@Nullable
	@Override
	public MediaType contentType() {
		return null;
	}

	/**
	 * Returns the number of bytes in that will returned by {@link #bytes}, or {@link #byteStream}, or
	 * -1 if unknown.
	 */
	@Override
	public long contentLength() {
		return 0;
	}

	@Override
	public BufferedSource source() {
		return null;
	}
}
