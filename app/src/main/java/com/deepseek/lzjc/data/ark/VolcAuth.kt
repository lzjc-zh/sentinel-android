package com.deepseek.lzjc.data.ark

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 火山引擎 HMAC-SHA256 签名认证
 * 参考: https://www.volcengine.com/docs/6313/170641
 */
object VolcAuth {

    private const val SERVICE = "ark"
    private const val REGION = "cn-beijing"
    private const val ALGORITHM = "HMAC-SHA256"
    private const val HOST = "ark.cn-beijing.volcengineapi.com"

    /**
     * 生成请求签名所需的 Headers
     */
    fun generateAuthHeaders(
        accessKeyId: String,
        secretAccessKey: String,
        action: String,
        body: String
    ): Map<String, String> {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val xDate = dateFormat.format(now)
        val dateStr = dateStamp.format(now)

        // 1. 计算 payload hash
        val payloadHash = sha256Hex(body)

        // 2. 构建规范请求
        val httpMethod = "POST"
        val canonicalUri = "/"
        val canonicalQueryString = "Action=$action&Version=2024-01-01"
        
        // CanonicalHeaders 按字母顺序排列
        val canonicalHeaders = buildString {
            append("content-type:application/json\n")
            append("host:$HOST\n")
            append("x-content-sha256:$payloadHash\n")
            append("x-date:$xDate\n")
        }
        
        // SignedHeaders 按字母顺序
        val signedHeaders = "content-type;host;x-content-sha256;x-date"

        val canonicalRequest = "$httpMethod\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        // 3. 构建待签名字符串
        val credentialScope = "$dateStr/$REGION/$SERVICE/request"
        val stringToSign = "$ALGORITHM\n$xDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        // 4. 计算签名
        val kDate = hmacSHA256(secretAccessKey.toByteArray(StandardCharsets.UTF_8), dateStr)
        val kRegion = hmacSHA256(kDate, REGION)
        val kService = hmacSHA256(kRegion, SERVICE)
        val kSigning = hmacSHA256(kService, "request")
        val signature = hmacSHA256Hex(kSigning, stringToSign)

        // 5. 构建 Authorization header
        val authorization = "$ALGORITHM Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        return mapOf(
            "Content-Type" to "application/json",
            "Host" to HOST,
            "X-Date" to xDate,
            "X-Content-Sha256" to payloadHash,
            "Authorization" to authorization
        )
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSHA256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSHA256Hex(key: ByteArray, data: String): String {
        val hmac = hmacSHA256(key, data)
        return hmac.joinToString("") { "%02x".format(it) }
    }
}
