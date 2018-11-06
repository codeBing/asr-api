package com.ecarx.asrapi.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author ITACHY
 * @date 2018/10/31
 * @desc TO-DO
 */

@Data
public class ATParam implements Serializable {

	private String ak;

	private String cn;

	private String rootKey;

	private String encytVer;

	private String loc;

	private String vin;

	private String o;

	private String dd;

	private String model;

	private String pkg;

	private String appver;

	private String imei;

	private String mac;

	private String bm;

	private String net;

	private int os_type;

	private String cs;

	private int w;

	private int h;

	private String sv;

	private String cm;

	private String data;

	private long create_time;

	private String retry_content;

}
