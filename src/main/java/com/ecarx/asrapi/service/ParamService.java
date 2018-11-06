package com.ecarx.asrapi.service;

import com.alibaba.fastjson.JSON;
import com.ecarx.asrapi.configs.ASRConfig;
import com.ecarx.asrapi.consts.EnvConsts;
import com.ecarx.asrapi.dto.ASRPam;
import com.ecarx.asrapi.dto.ATParam;
import com.ecarx.asrapi.dto.AuthParam;
import com.ecarx.asrapi.dto.BEParam;
import com.ecarx.asrapi.dto.nano.ASR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author ITACHY
 * @date 2018/11/3
 * @desc TO-DO
 */

@Service
public class ParamService {

	@Resource
	private RsaService rsaService;

	@Resource
	private ASRConfig config;

	private static Logger log = LoggerFactory.getLogger(ParamService.class);

	public List<ASR.APIRequest> buildData(byte[] data) {
		List<ASR.APIRequest> requests = new ArrayList<>();
		requests.add(biuldReqAuth());
		requests.addAll(buildAudioData(data));
		requests.add(buildReqLast());
		return requests;
	}

	public List<ASR.APIRequest> buildActv() {
		List<ASR.APIRequest> requests = new ArrayList<>();
		requests.add(biuldReqParam());
		requests.add(buildReqLast());
		return requests;
	}

	public List<ASR.APIRequest> buildActvWithAudio(byte[] data) {
		List<ASR.APIRequest> requests = new ArrayList<>();
		requests.add(biuldReqParam());
		requests.addAll(buildAudioData(data));
		requests.add(buildReqLast());
		return requests;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build audio data
	 */
	private List<ASR.APIRequest> buildAudioData(byte[] data) {
		List<ASR.APIRequest> requests = new ArrayList<>();
		//分段构造音频数据包
		byte[] blocks;
		int    offset = 0, blockSize = config.getBlockSize();
		int    loops  = data.length / blockSize;
		while (loops > 0) {
			loops--;
			blocks = new byte[blockSize];
			System.arraycopy(data, offset, blocks, 0, blockSize);
			offset += blockSize;
			requests.add(buildReqesut(EnvConsts.REQ_DATA, blocks));
		}
		//处理剩余的数据包
		blocks = new byte[offset == 0 ? data.length : data.length - offset];
		if (blocks.length > 0) {
			System.arraycopy(data, offset, blocks, 0, blocks.length);
			requests.add(buildReqesut(EnvConsts.REQ_DATA, blocks));
		}
		log.info("Offset length: {}", offset / blockSize);
		return requests;
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build auth
	 */
	private ASR.APIRequest biuldReqAuth() {
		return buildReqesut(EnvConsts.REQ_AUTH, new HashMap<>());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build param
	 */
	private ASR.APIRequest biuldReqParam() {
		return buildReqesut(EnvConsts.REQ_PARAM, new HashMap<>());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build last
	 */
	private ASR.APIRequest buildReqLast() {
		return buildReqesut(EnvConsts.REQ_LAST, new HashMap<>());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build third data
	 */
	private ASR.APIRequest buildReqThirdData() {
		return buildReqesut(EnvConsts.REQ_THIRD_DATE, new HashMap<>());
	}

	/**
	 * @author ITACHY
	 * @date 2018/11/3
	 * @desc build all kinds of requests
	 */
	private ASR.APIRequest buildReqesut(String type, byte[] data) {
		return buildReqesut(type, data, null);
	}

	private ASR.APIRequest buildReqesut(String type, Map<String, String> options) {
		return buildReqesut(type, null, options);
	}

	private ASR.APIRequest buildReqesut(String type, byte[] data, Map<String, String> options) {

		ASR.ApiParam   apiParam;
		ASR.APIRequest request = new ASR.APIRequest();

		switch (type) {
		case EnvConsts.REQ_DATA:
			request.apiReqType = ASR.API_REQ_TYPE_DATA;
			//构建数据
			ASR.ApiData apiData = new ASR.ApiData();
			apiData.len = data.length;
			apiData.postData = data;
			request.data = apiData;
			log.info("build audio package");
			return request;
		case EnvConsts.REQ_PARAM:
			request.apiReqType = ASR.API_REQ_TYPE_PARAM;
			//构建激活
			apiParam = buildAPIParam(EnvConsts.ASR_ACTV, null);
			request.param = apiParam;
			log.info("build param package");
			return request;
		case EnvConsts.REQ_AUTH:
			request.apiReqType = ASR.API_REQ_TYPE_PARAM;

			//构建鉴权
			apiParam = buildAPIParam(EnvConsts.ASR_AUTH, null);
			request.param = apiParam;
			log.info("build auth package");
			return request;
		case EnvConsts.REQ_LAST:
			request.apiReqType = ASR.API_REQ_TYPE_LAST;

			//构建Last
			ASR.ApiLast apiLast = new ASR.ApiLast();
			request.last = apiLast;
			log.info("build last package");
			return request;
		case EnvConsts.REQ_CANCEL:
			request.apiReqType = ASR.API_REQ_TYPE_CANCEL;

			//构建Cancel
			ASR.ApiCancel apiCancel = new ASR.ApiCancel();
			request.cancel = apiCancel;
			log.info("build cancel package");
			return request;
		case EnvConsts.REQ_THIRD_DATE:
			request.apiReqType = ASR.API_REQ_TYPE_THIRD_DATA;

			//构建ThirdData
			ASR.ApiThirdData thirdData = new ASR.ApiThirdData();
			thirdData.len = 1;
			thirdData.type = "2";
			thirdData.thirdData = new byte[0];
			request.thirdData = thirdData;
			log.info("build third-data package");
			return request;
		default:
			return null;
		}

	}

	private ASR.ApiParam buildAPIParam(String type, Map<String, String> options) {

		ASR.ApiParam apiParam = new ASR.ApiParam();
		apiParam.cuid = UUID.randomUUID().toString();
		apiParam.chunkKey = "com.baidu.open_asr_test";
		apiParam.sampleRate = 16000;
		apiParam.format = "pcm";
		apiParam.taskId = -12345;
		apiParam.earlyReturn = false;

		//构建pam
		ASRPam pam = new ASRPam();
		pam.setAsr_backend_type("iov");
		List<BEParam> beParams = new ArrayList<>();
		BEParam       beParam  = new BEParam();
		beParam.setAk("test");
		beParam.setAv(12);
		beParam.setCn("300000000");
		beParam.setCoordtype(2);
		beParam.setCrd("113.948913_22.530194");
		beParams.add(beParam);
		pam.setAsr_backend_param(beParams);

		if (EnvConsts.ASR_ACTV.equals(type)) {
			ATParam activeParam = new ATParam();
			activeParam.setAk("ecarx");
			activeParam.setCn("300000000");
			activeParam.setRootKey("35adc998673b4fa4aeb7143daa8610d6");
			activeParam.setEncytVer("1.0");
			activeParam.setLoc("113.948913_22.530194");
			activeParam.setVin("XXX123456");
			activeParam.setO("Android");
			activeParam.setModel("XXX654321");
			activeParam.setPkg("testpkg");
			activeParam.setAppver("XXX");
			activeParam.setImei("355065 05 331100 1/01");
			activeParam.setMac("00-01-6C-06-A6-29");
			activeParam.setBm("000A3A58F310");
			activeParam.setDd("abcdefg123456");
			activeParam.setW(6);
			activeParam.setH(16);
			activeParam.setSv("1-1-1");
			activeParam.setNet("4G");
			activeParam.setCs("XXX123456");
			activeParam.setCm("XXX");
			activeParam.setData("XXX");
			activeParam.setCreate_time(System.currentTimeMillis());
			try {
				String jsonString = JSON.toJSONString(activeParam);
				log.info("ASR_ACTIVE_PARAM json: {}", jsonString);
				String encString = rsaService.encryptByPulicSplit(jsonString);
				log.info("密文: {}", encString);
				pam.setAsr_active_param(encString);
			} catch (Exception e) {
				log.info("Encrypt failed:", e);
				return null;
			}
		}

		if (EnvConsts.ASR_AUTH.equals(type)) {
			List<AuthParam> authParams = new ArrayList<>();
			AuthParam       authParam  = new AuthParam();
			authParam.setToken("T4092149880773836832");
			authParam.setUuid("0x40921423804170240");
			authParam.setPkg("testpkg");
			authParams.add(authParam);
			pam.setAsr_auth_param(authParams);
		}
		return apiParam;
	}

}
