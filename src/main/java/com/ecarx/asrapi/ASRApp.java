package com.ecarx.asrapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;

@SpringBootApplication
public class ASRApp {

	/*@Bean
	ProtobufHttpMessageConverter protobufHttpMessageConverter() {
		return new ProtobufHttpMessageConverter();
	}*/

	@Bean
	ProtobufJsonFormatHttpMessageConverter protobufHttpMessageConverter() {
		return new ProtobufJsonFormatHttpMessageConverter();
	}

	public static void main(String[] args) {
		SpringApplication.run(ASRApp.class, args);
	}
}
