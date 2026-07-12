package ai.dpdpguard.server

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookVerifierTest {
	private val secret = "whsec_test123"
	private val body = """{"eventType":"consent.given","organizationId":"org_abc","payload":{},"timestamp":1700000000000}"""

	private fun hmacHex(key: String, message: String): String {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
		return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
	}

	@Test
	fun `accepts a signature computed the same way convex webhooks ts signs it`() {
		val signature = hmacHex(secret, body)
		assertTrue(WebhookVerifier.verify(secret, body, signature))
	}

	@Test
	fun `rejects a signature computed with the wrong secret`() {
		val signature = hmacHex("wrong-secret", body)
		assertFalse(WebhookVerifier.verify(secret, body, signature))
	}

	@Test
	fun `rejects a signature for a tampered body`() {
		val signature = hmacHex(secret, body)
		val tampered = body.replace("consent.given", "consent.withdrawn")
		assertFalse(WebhookVerifier.verify(secret, tampered, signature))
	}

	@Test
	fun `rejects a missing signature header`() {
		assertFalse(WebhookVerifier.verify(secret, body, null))
		assertFalse(WebhookVerifier.verify(secret, body, ""))
	}

	@Test
	fun `rejects a malformed signature without throwing`() {
		assertFalse(WebhookVerifier.verify(secret, body, "not-a-real-signature"))
	}
}
