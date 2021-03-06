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
@ConfigurationProperties(prefix = "nlu")
public class NLUConfig {

	private String url;

	private Double version;

	private Integer timeout;

	private String protocol;

}
