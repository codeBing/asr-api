package com.ecarx.asrapi.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author ITACHY
 * @date 2018/10/31
 * @desc TO-DO
 */

@Data
public class ASRPam implements Serializable {

	private String asr_backend_type;

	private List<BEParam> asr_backend_param;

	private String asr_active_param;

	private List<AuthParam> asr_auth_param;

}
