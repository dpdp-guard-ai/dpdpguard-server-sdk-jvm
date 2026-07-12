package ai.dpdpguard.server

import ai.dpdpguard.server.generated.model.CreateDsrRequestRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DpdpGuardClientTest {
	private lateinit var server: MockWebServer

	@BeforeTest
	fun start() {
		server = MockWebServer()
		server.start()
	}

	@AfterTest
	fun stop() {
		server.shutdown()
	}

	private fun baseUrl(): String = server.url("/").toString().removeSuffix("/")

	@Test
	fun `getOrganization calls the public endpoint with no Authorization header`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody("""{"orgId":"org_1","name":"Acme","slug":"acme"}""")
				.setHeader("Content-Type", "application/json"),
		)
		val client = DpdpGuardClient(baseUrl = baseUrl())

		val org = client.getOrganization("acme")

		assertEquals("org_1", org.orgId)
		val recorded = server.takeRequest()
		assertNull(recorded.getHeader("Authorization"))
	}

	@Test
	fun `brokerToken authenticates with the API key and stores the returned access token`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(201)
				.setBody("""{"accessToken":"brokered-token-abc","expiresAt":1234567890,"tokenType":"Bearer"}""")
				.setHeader("Content-Type", "application/json"),
		)
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody("""{"requests":[]}""")
				.setHeader("Content-Type", "application/json"),
		)
		val client = DpdpGuardClient(baseUrl = baseUrl(), apiKey = "svc_key_123")

		val token = client.brokerToken("fiduciary-user-1")
		assertEquals("brokered-token-abc", token)

		val brokerRequest = server.takeRequest()
		assertEquals("Bearer svc_key_123", brokerRequest.getHeader("Authorization"))

		client.listDsrRequests()
		val dsrRequest = server.takeRequest()
		assertEquals("Bearer brokered-token-abc", dsrRequest.getHeader("Authorization"))
	}

	@Test
	fun `an authenticated call without a prior brokerToken or constructor token surfaces the server's 401 as a typed error`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(401)
				.setBody("""{"code":"UNAUTHORIZED","error":"Missing access token"}""")
				.setHeader("Content-Type", "application/json"),
		)
		val client = DpdpGuardClient(baseUrl = baseUrl())

		val error = assertFailsWith<DpdpGuardApiError> { client.listDsrRequests() }
		assertEquals(ApiErrorCode.UNAUTHORIZED, error.code)
		assertEquals(401, error.status)
	}

	@Test
	fun `a non-2xx response is thrown as a typed DpdpGuardApiError`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(404)
				.setBody("""{"code":"NOT_FOUND","error":"No such organization"}""")
				.setHeader("Content-Type", "application/json"),
		)
		val client = DpdpGuardClient(baseUrl = baseUrl())

		val error = assertFailsWith<DpdpGuardApiError> { client.getOrganization("missing") }
		assertEquals(ApiErrorCode.NOT_FOUND, error.code)
		assertEquals(404, error.status)
	}

	@Test
	fun `createDsrRequest forwards an Idempotency-Key header when provided`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(201)
				.setBody("""{"requestId":"dsr_1","type":"erasure","status":"pending"}""")
				.setHeader("Content-Type", "application/json"),
		)
		val client = DpdpGuardClient(baseUrl = baseUrl(), accessToken = "token-abc")

		client.createDsrRequest(
			organizationId = "org_1",
			type = CreateDsrRequestRequest.Type.erasure,
			idempotencyKey = "idem-key-1",
		)

		val recorded = server.takeRequest()
		assertEquals("idem-key-1", recorded.getHeader("Idempotency-Key"))
	}

	@Test
	fun `getNomination returns null when the server responds with a null body`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody("null")
				.setHeader("Content-Type", "application/json"),
		)
		val client = DpdpGuardClient(baseUrl = baseUrl(), accessToken = "token-abc")

		assertNull(client.getNomination())
	}
}
