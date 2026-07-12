package ai.dpdpguard.server

/**
 * Pure helper for gating a feature/tracker on an existing consent decision —
 * e.g. `if (!hasConsent(consents, "Analytics")) return` before firing an
 * analytics event. Does not call the network.
 */
data class ConsentRecord(
	val purpose: String,
	/** Present when the decision was withdrawn; null means still active. */
	val withdrawnAt: Long? = null,
)

fun hasConsent(consents: List<ConsentRecord>, purpose: String): Boolean =
	consents.any { it.purpose == purpose && it.withdrawnAt == null }
