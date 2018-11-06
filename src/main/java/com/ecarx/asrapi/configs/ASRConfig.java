package com.ecarx.asrapi.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author ITACHY
 * @date 2018/11/3
 * @desc define config-param class
 */

@Data
@Configuration
@ConfigurationProperties(prefix = "asr")
public class ASRConfig {

	//@Value("${threads}")
	private Integer threads;

	//@Value("${blockSize}")
	private Integer blockSize;

	//@Value("${upUrl}")
	private String upUrl;

	//@Value("${downUrl}")
	private String downUrl;

}
