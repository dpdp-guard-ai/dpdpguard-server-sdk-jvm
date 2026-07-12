package ai.dpdpguard.server

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drift guard for [ApiErrorCode]: fetches @dpdpguard/contract's published
 * conformance/error-catalog.json (ADR-002 D2) and asserts this hand-mirrored
 * enum still lists exactly the same codes, in the same order.
 */
class ApiErrorCodeTest {
	private data class CatalogEntry(val code: String, val description: String)
	private data class CatalogFile(val codes: List<CatalogEntry>)

	private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

	@Test
	fun `ApiErrorCode matches the published error catalog`() {
		val json = URI("https://unpkg.com/@dpdpguard/contract@0.2.0/conformance/error-catalog.json")
			.toURL()
			.openStream()
			.bufferedReader()
			.use { it.readText() }
		val catalog = moshi.adapter(CatalogFile::class.java).fromJson(json)!!

		assertEquals(catalog.codes.map { it.code }, ApiErrorCode.entries.map { it.name })
	}
}
