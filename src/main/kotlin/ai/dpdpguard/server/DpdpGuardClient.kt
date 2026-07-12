package ai.dpdpguard.server

import ai.dpdpguard.server.generated.api.DefaultApi
import ai.dpdpguard.server.generated.infrastructure.ClientError
import ai.dpdpguard.server.generated.infrastructure.ClientException
import ai.dpdpguard.server.generated.infrastructure.ServerError
import ai.dpdpguard.server.generated.infrastructure.ServerException
import ai.dpdpguard.server.generated.infrastructure.Success
import ai.dpdpguard.server.generated.model.ApiError
import ai.dpdpguard.server.generated.model.BrokerTokenRequest
import ai.dpdpguard.server.generated.model.CreateDsrRequestRequest
import ai.dpdpguard.server.generated.model.CreateGrievanceRequest
import ai.dpdpguard.server.generated.model.DsrRequest
import ai.dpdpguard.server.generated.model.GetBannerConfig200Response
import ai.dpdpguard.server.generated.model.Grievance
import ai.dpdpguard.server.generated.model.LinkAnonymousConsent200Response
import ai.dpdpguard.server.generated.model.LinkAnonymousConsentRequest
import ai.dpdpguard.server.generated.model.Nomination
import ai.dpdpguard.server.generated.model.Notice
import ai.dpdpguard.server.generated.model.OrgSummary
import ai.dpdpguard.server.generated.model.UpsertNominationRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient

/**
 * Thin typed client over DPDP Guard's public `/api/v1` (spec §4.2). Wraps
 * the OpenAPI-Generator-generated `DefaultApi` (generated/, regenerated
 * from @dpdpguard/contract's openapi/v1.yaml — see build.gradle.kts) in
 * ergonomic methods, and maps every non-2xx response to a
 * [DpdpGuardApiError] keyed by the ADR-002 error catalog's `code`.
 *
 * Unlike the raw generated client (which stores `accessToken`/`apiKey` as
 * JVM-wide companion-object statics — unsafe for a multi-tenant server
 * process handling multiple organizations concurrently), this class scopes
 * auth to its own OkHttp interceptor closure, so concurrent
 * [DpdpGuardClient] instances never race on each other's tokens.
 */
class DpdpGuardClient(
	baseUrl: String,
	private val apiKey: String? = null,
	accessToken: String? = null,
) {
	@Volatile
	private var accessToken: String? = accessToken

	private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
	private val errorAdapter = moshi.adapter(ApiError::class.java)

	private val okHttpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val original = chain.request()
			val isBrokerTokenCall = original.url.encodedPath.endsWith("/api/v1/auth/broker-token")
			val token = if (isBrokerTokenCall) apiKey else accessToken
			System.err.println("DEBUG interceptor: path=${original.url.encodedPath} isBrokerTokenCall=$isBrokerTokenCall apiKey=$apiKey accessTokenField=$accessToken token=$token")
			val request = if (token != null) {
				original.newBuilder().header("Authorization", "Bearer $token").build()
			} else {
				original
			}
			chain.proceed(request)
		}
		.build()

	private val api = DefaultApi(basePath = baseUrl, client = okHttpClient)

	/** Set/replace the brokered principal access token (e.g. after re-brokering on expiry). */
	fun setAccessToken(token: String) {
		accessToken = token
	}

	private fun <T> rethrowAsTyped(block: () -> T): T =
		try {
			block()
		} catch (e: ClientException) {
			throw toApiError(e.response as? ClientError<*>, e.statusCode, e.message)
		} catch (e: ServerException) {
			throw toApiError(e.response as? ServerError<*>, e.statusCode, e.message)
		}

	private fun toApiError(errorResponse: Any?, status: Int, fallbackMessage: String?): DpdpGuardApiError {
		val body = when (errorResponse) {
			is ClientError<*> -> errorResponse.body as? String
			is ServerError<*> -> errorResponse.body as? String
			else -> null
		}
		val parsed = body?.let { runCatching { errorAdapter.fromJson(it) }.getOrNull() }
		val rawCode = parsed?.code ?: "VALIDATION_ERROR"
		return DpdpGuardApiError(
			code = ApiErrorCode.fromWire(rawCode),
			rawCode = rawCode,
			message = parsed?.error ?: fallbackMessage ?: "Request failed with status $status",
			status = status,
		)
	}

	// --- Public reads (no auth) ---

	fun getOrganization(slug: String): OrgSummary = rethrowAsTyped { api.getOrgBySlug(slug) }

	fun getNotices(orgId: String): List<Notice> =
		rethrowAsTyped { api.getNoticesForOrg(orgId).notices }

	fun getNotice(noticeId: String): Notice = rethrowAsTyped { api.getNoticeById(noticeId) }

	fun getBannerConfig(
		orgId: String,
		domain: String? = null,
		appId: String? = null,
	): GetBannerConfig200Response = rethrowAsTyped { api.getBannerConfig(orgId, domain, appId) }

	// --- Auth (ADR-004) ---

	/** Mints a brokered principal access token using the service API key, and stores it for subsequent calls. */
	fun brokerToken(externalId: String): String {
		requireNotNull(apiKey) { "DpdpGuardClient: this call requires `apiKey` to be set in the constructor." }
		val result = rethrowAsTyped { api.brokerToken(BrokerTokenRequest(externalId = externalId)) }
		accessToken = result.accessToken
		return result.accessToken
	}

	fun linkAnonymousConsent(anonymousId: String): LinkAnonymousConsent200Response =
		rethrowAsTyped { api.linkAnonymousConsent(LinkAnonymousConsentRequest(anonymousId = anonymousId)) }

	// --- DSR (spec §4.2) ---

	fun listDsrRequests(): List<DsrRequest> =
		rethrowAsTyped { api.listDsrRequestsForCurrentUser().requests }

	fun createDsrRequest(
		organizationId: String,
		type: CreateDsrRequestRequest.Type,
		details: String? = null,
		idempotencyKey: String? = null,
	): DsrRequest = rethrowAsTyped {
		api.createDsrRequest(
			CreateDsrRequestRequest(organizationId = organizationId, type = type, details = details),
			idempotencyKey,
		)
	}

	// --- Grievances (spec §4.2) ---

	fun listGrievances(): List<Grievance> =
		rethrowAsTyped { api.listGrievancesForCurrentUser().grievances }

	fun createGrievance(organizationId: String, subject: String, description: String): Grievance =
		rethrowAsTyped {
			api.createGrievance(
				CreateGrievanceRequest(organizationId = organizationId, subject = subject, description = description),
			)
		}

	// --- Nomination (spec §4.2) ---

	/** Null if the caller has no active nomination — the generated `getNomination()` doesn't model that, so this goes through `WithHttpInfo` directly. */
	fun getNomination(): Nomination? = rethrowAsTyped {
		when (val response = api.getNominationWithHttpInfo()) {
			is Success<*> -> response.data as Nomination?
			else -> throw IllegalStateException("unreachable: non-success handled by rethrowAsTyped")
		}
	}

	fun upsertNomination(nomineeName: String, nomineeContact: String): Nomination =
		rethrowAsTyped {
			api.upsertNomination(UpsertNominationRequest(nomineeName = nomineeName, nomineeContact = nomineeContact))
		}

	fun revokeNomination(): Boolean = rethrowAsTyped { api.revokeNomination().revoked }
}
