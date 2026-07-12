package ai.dpdpguard.server

/**
 * ADR-002 D2's typed error-code enum. Hand-mirrored from
 * conformance/error-catalog.json (there's no JVM-native way to load an
 * arbitrary classpath-less JSON resource from a published npm package at
 * runtime) — ApiErrorCodeTest fetches that file at test time and asserts
 * this list stays in sync with it.
 */
enum class ApiErrorCode {
	MINOR_TRACKING_BLOCKED,
	NOTICE_NOT_PUBLISHED,
	ALREADY_CONSENTED,
	NOT_ASSOCIATED_WITH_ORG,
	INVALID_STATUS_TRANSITION,
	SDK_VERSION_UNSUPPORTED,
	NOT_FOUND,
	UNAUTHORIZED,
	RATE_LIMITED,
	VALIDATION_ERROR,
	;

	companion object {
		/** Falls back to VALIDATION_ERROR for a code this SDK doesn't recognize yet. */
		fun fromWire(code: String): ApiErrorCode = entries.find { it.name == code } ?: VALIDATION_ERROR
	}
}

/** Thrown by [DpdpGuardClient] for any non-2xx `/api/v1` response. */
class DpdpGuardApiError(
	val code: ApiErrorCode,
	val rawCode: String,
	message: String,
	val status: Int,
) : RuntimeException(message)
