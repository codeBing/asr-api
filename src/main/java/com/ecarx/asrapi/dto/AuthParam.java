package com.ecarx.asrapi.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author ITACHY
 * @date 2018/10/31
 * @desc TO-DO
 */

@Data
public class AuthParam implements Serializable {

	private String uuid;

	private String token;

	private String pkg;
}
