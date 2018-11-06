package com.ecarx.asrapi.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author ITACHY
 * @date 2018/10/31
 * @desc TO-DO
 */

@Data
public class BEParam implements Serializable {

	private String ak;

	private int av;

	private String cn;

	private int coordtype;

	private String crd;
}
