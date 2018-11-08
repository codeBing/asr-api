package com.ecarx.asrapi.service;

import com.ecarx.asrapi.configs.ASRConfig;
import com.sun.istack.internal.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author ITACHY
 * @date 2018/11/7
 * @desc support multi-thread for asr/nlu
 */

@Service
public class ThreadService {

	private final ThreadPoolExecutor executor;

	@Autowired
	public ThreadService(final ASRConfig config) {
		this.executor = new ScheduledThreadPoolExecutor(config.getThreads());
	}

	public void execute(@NotNull Runnable task) {
		executor.execute(task);
	}

	public Future<?> submit(@NotNull Runnable task) {
		return executor.submit(task);
	}

	public <T> Future<T> submit(Runnable task, T result) {
		return executor.submit(task, result);
	}

}
