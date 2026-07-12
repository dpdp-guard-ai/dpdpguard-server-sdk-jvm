package ai.dpdpguard.server

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the `X-DPDP-Signature` header set by convex/webhooks.ts's
 * `deliverToEndpoint` (HMAC-SHA256 over the raw JSON body, hex-encoded,
 * using the webhook endpoint's own secret — not the audit-hash secret).
 *
 * [rawBody] must be the exact bytes received on the wire, before any JSON
 * parsing — HMACs are sensitive to whitespace/key-order, so re-serializing
 * a parsed object and hashing that will not match.
 */
object WebhookVerifier {
	fun verify(secret: String, rawBody: String, signatureHeader: String?): Boolean {
		if (signatureHeader.isNullOrEmpty()) return false

		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
		val expected = mac.doFinal(rawBody.toByteArray(Charsets.UTF_8))
		val actual = try {
			decodeHex(signatureHeader)
		} catch (e: IllegalArgumentException) {
			return false
		}

		if (expected.size != actual.size) return false
		return MessageDigest.isEqual(expected, actual)
	}

	private fun decodeHex(hex: String): ByteArray {
		require(hex.length % 2 == 0) { "hex string must have an even length" }
		return ByteArray(hex.length / 2) { i ->
			((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
		}
	}
}
