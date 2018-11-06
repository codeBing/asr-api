package com.ecarx.asrapi.interfaces;

import okio.Buffer;

/**
 * @author ITACHY
 * @date 2018/11/1
 * @desc define response callback
 */
public interface ResponseCallBack {

	void read(Buffer sink, long byteCount);
}
