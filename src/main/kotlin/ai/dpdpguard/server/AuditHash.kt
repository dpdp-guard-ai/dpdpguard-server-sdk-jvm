package ai.dpdpguard.server

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * JVM reimplementation of convex/lib/auditHash.ts's canonicalization
 * (conformance/audit-hash-spec.md), so a server SDK holding the same
 * DPDP_AUDIT_HASH_HMAC_SECRET can independently verify a consentAuditTrail
 * row's auditHash rather than trusting it blindly. Verified against
 * @dpdpguard/contract's golden vectors in AuditHashTest (ADR-002 D5).
 */
data class AuditHashInput(
	val organizationId: String,
	val noticeId: String,
	val noticeVersion: Int,
	val purpose: String,
	val dataTypes: List<String>,
	val givenAt: Long,
	val source: String?,
)

object AuditHash {
	fun canonicalize(input: AuditHashInput): String {
		val sortedDataTypes = input.dataTypes.sorted().joinToString(",")
		val source = input.source ?: "null"
		return listOf(
			input.organizationId,
			input.noticeId,
			input.noticeVersion.toString(),
			input.purpose,
			sortedDataTypes,
			input.givenAt.toString(),
			source,
		).joinToString("|")
	}

	fun compute(input: AuditHashInput, secret: String): String {
		val canonical = canonicalize(input)
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
		val digest = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
		return digest.joinToString("") { "%02x".format(it) }
	}
}
