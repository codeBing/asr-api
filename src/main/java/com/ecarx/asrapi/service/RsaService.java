package com.ecarx.asrapi.service;

import com.ecarx.asrapi.utils.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author ITACHY
 * @date 2018/10/31
 * @desc RSA加密算法
 */

@Service
public final class RsaService {

	@Value("${asr.encrypt.keypair}")
	private String keyPair;

	@Value("${asr.encrypt.cipher}")
	private String cipher;
	// RSA最大加密明文大小
	@Value("${asr.encrypt.encBlock}")
	private int    encBlock;
	// RSA最大解密密文大小
	@Value("${asr.encrypt.decBlock}")
	private int    decBlock;

	@Value("${asr.encrypt.pubkey}")
	private String pubkey;

	/**
	 * 随机生成RSA密钥对(默认密钥长度为1024)
	 * @return
	 */
	public KeyPair generateRSAKeyPair() {
		return generateRSAKeyPair(1024);
	}

	/**
	 * 随机生成RSA密钥对
	 * @param keyLength 密钥长度，范围：512～2048 一般1024
	 * @return
	 */
	public KeyPair generateRSAKeyPair(int keyLength) {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyPair);
			kpg.initialize(keyLength);
			return kpg.genKeyPair();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 用公钥加密 <br>
	 * 每次加密的字节数，不能超过密钥的长度值减去11
	 * @param content      需加密数据的byte数据
	 * @param publicKeyStr 公钥
	 * @return 加密后的byte型数据
	 */
	public String encryptByPublic(String content, String publicKeyStr) throws Exception {
		PublicKey publicKey = loadPublicKey(publicKeyStr);
		Cipher    cipher    = Cipher.getInstance(this.cipher);
		// 编码前设定编码方式及密钥
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		// 传入编码数据并返回编码结果
		byte[] data         = content.getBytes();
		byte[] encryptBytes = cipher.doFinal(data);
		return Base64.encode(encryptBytes);

	}

	/**
	 * 用私钥解密
	 * @param encryptedData 经过encryptedData()加密返回的byte数据
	 * @param privateKeyStr 私钥
	 * @return
	 */
	public String decryptByPrivate(String encryptedData, String privateKeyStr) throws Exception {
		PrivateKey privateKey = loadPrivateKey(privateKeyStr);
		Cipher     cipher     = Cipher.getInstance(this.cipher);
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		byte[] encryptByte = Base64.decode(encryptedData);
		byte[] decyptBytes = cipher.doFinal(encryptByte);
		return new String(decyptBytes);
	}

	/**
	 * RSA分段加密
	 * @param content 待加密文本
	 * @return 密文
	 * @throws Exception exception
	 */
	public String encryptByPulicSplit(String content) throws Exception {
		PublicKey publicKey = loadPublicKey(pubkey);
		Cipher    cipher    = Cipher.getInstance(this.cipher); // java默认"RSA"="RSA/ECB/PKCS1Padding"
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		byte[]                data     = content.getBytes();
		int                   inputLen = data.length;
		ByteArrayOutputStream out      = new ByteArrayOutputStream();
		int                   offSet   = 0;
		byte[]                cache;
		int                   i        = 0;
		// 对数据分段加密
		while (inputLen - offSet > 0) {
			if (inputLen - offSet > encBlock) {
				cache = cipher.doFinal(data, offSet, encBlock);
			} else {
				cache = cipher.doFinal(data, offSet, inputLen - offSet);
			}
			out.write(cache, 0, cache.length);
			i++;
			offSet = i * encBlock;
		}
		byte[] encryptedData = out.toByteArray();
		out.close();
		return Base64.encode(encryptedData);
	}

	/**
	 * RSA分段解密
	 * @param content       密文
	 * @param privateKeyStr 私钥
	 * @return 明文
	 * @throws Exception exception
	 */
	public String decryptByPrivateSplit(String content, String privateKeyStr) throws Exception {
		PrivateKey privateKey = loadPrivateKey(privateKeyStr);
		Cipher     cipher     = Cipher.getInstance(this.cipher);
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		byte[]                encryptedData = Base64.decode(content);
		int                   inputLen      = encryptedData.length;
		ByteArrayOutputStream out           = new ByteArrayOutputStream();
		int                   offSet        = 0;
		byte[]                cache;
		int                   i             = 0;
		// 对数据分段解密
		while (inputLen - offSet > 0) {
			if (inputLen - offSet > decBlock) {
				cache = cipher.doFinal(encryptedData, offSet, decBlock);
			} else {
				cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
			}
			out.write(cache, 0, cache.length);
			i++;
			offSet = i * decBlock;
		}
		byte[] decryptedData = out.toByteArray();
		out.close();
		return new String(decryptedData);
	}

	/**
	 * 从字符串中加载公钥
	 * @param publicKeyStr 公钥数据字符串
	 * @throws Exception 加载公钥时产生的异常
	 */
	public PublicKey loadPublicKey(String publicKeyStr) throws Exception {
		try {
			byte[]             buffer     = Base64.decode(publicKeyStr);
			KeyFactory         keyFactory = KeyFactory.getInstance(keyPair);
			X509EncodedKeySpec keySpec    = new X509EncodedKeySpec(buffer);
			return keyFactory.generatePublic(keySpec);
		} catch (NoSuchAlgorithmException e) {
			throw new Exception("无此算法");
		} catch (InvalidKeySpecException e) {
			throw new Exception("公钥非法");
		} catch (NullPointerException e) {
			throw new Exception("公钥数据为空");
		}
	}

	/**
	 * 从文件中输入流中加载公钥
	 * @param in 公钥输入流
	 * @throws Exception 加载公钥时产生的异常
	 */
	public PublicKey loadPublicKey(InputStream in) throws Exception {
		try {
			return loadPublicKey(readKey(in));
		} catch (IOException e) {
			throw new Exception("公钥数据流读取错误");
		} catch (NullPointerException e) {
			throw new Exception("公钥输入流为空");
		}
	}

	/**
	 * 从字符串中加载私钥<br>
	 * 加载时使用的是PKCS8EncodedKeySpec（PKCS#8编码的Key指令）。
	 * @param privateKeyStr
	 * @return
	 * @throws Exception
	 */
	public PrivateKey loadPrivateKey(String privateKeyStr) throws Exception {
		try {
			byte[] buffer = Base64.decode(privateKeyStr);
			// X509EncodedKeySpec keySpec = new X509EncodedKeySpec(buffer);
			PKCS8EncodedKeySpec keySpec    = new PKCS8EncodedKeySpec(buffer);
			KeyFactory          keyFactory = KeyFactory.getInstance(keyPair);
			return keyFactory.generatePrivate(keySpec);
		} catch (NoSuchAlgorithmException e) {
			throw new Exception("无此算法");
		} catch (InvalidKeySpecException e) {
			throw new Exception("私钥非法");
		} catch (NullPointerException e) {
			throw new Exception("私钥数据为空");
		}
	}

	/**
	 * 从文件中加载私钥
	 * @param
	 * @return 是否成功
	 * @throws Exception
	 */
	public PrivateKey loadPrivateKey(InputStream in) throws Exception {
		try {
			return loadPrivateKey(readKey(in));
		} catch (IOException e) {
			throw new Exception("私钥数据读取错误");
		} catch (NullPointerException e) {
			throw new Exception("私钥输入流为空");
		}
	}

	/**
	 * 读取密钥信息
	 * --------------------
	 * CONTENT
	 * --------------------
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private String readKey(InputStream in) throws IOException {
		BufferedReader br       = new BufferedReader(new InputStreamReader(in));
		String         readLine = null;
		StringBuilder  sb       = new StringBuilder();
		while ((readLine = br.readLine()) != null) {
			if (readLine.charAt(0) == '-') {
				continue;
			} else {
				sb.append(readLine);
				sb.append('\r');
			}
		}

		return sb.toString();
	}
}
