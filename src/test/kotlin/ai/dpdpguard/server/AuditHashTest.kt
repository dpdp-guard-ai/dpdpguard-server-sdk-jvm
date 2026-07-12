package ai.dpdpguard.server

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADR-002 D5: this SDK's CI must run @dpdpguard/contract's golden vectors as
 * a required gate — this is that gate. Any failure here means this SDK's
 * AuditHash implementation has drifted from convex/lib/auditHash.ts.
 *
 * Fetched directly from unpkg (same interim cross-ecosystem path as
 * build.gradle.kts's openApiGenerate spec download — see its comment) since
 * there is no Maven mirror of @dpdpguard/contract yet (ADR-002 D2).
 */
class AuditHashTest {
	private data class VectorFile(
		val testSecret: String,
		val vectors: List<Vector>,
	)

	private data class Vector(
		val name: String,
		val input: VectorInput,
		val expectedCanonical: String,
		val expectedHash: String,
	)

	private data class VectorInput(
		val organizationId: String,
		val noticeId: String,
		val noticeVersion: Int,
		val purpose: String,
		val dataTypes: List<String>,
		val givenAt: Long,
		val source: String?,
	)

	private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

	private fun fetchVectors(): VectorFile {
		val json = URI("https://unpkg.com/@dpdpguard/contract@0.2.0/conformance/audit-hash-vectors.json")
			.toURL()
			.openStream()
			.bufferedReader()
			.use { it.readText() }
		return moshi.adapter(VectorFile::class.java).fromJson(json)!!
	}

	@Test
	fun `golden vectors match canonicalization and HMAC hash`() {
		val file = fetchVectors()
		for (vector in file.vectors) {
			val input = AuditHashInput(
				organizationId = vector.input.organizationId,
				noticeId = vector.input.noticeId,
				noticeVersion = vector.input.noticeVersion,
				purpose = vector.input.purpose,
				dataTypes = vector.input.dataTypes,
				givenAt = vector.input.givenAt,
				source = vector.input.source,
			)
			assertEquals(vector.expectedCanonical, AuditHash.canonicalize(input), "canonical form for '${vector.name}'")
			assertEquals(vector.expectedHash, AuditHash.compute(input, file.testSecret), "hash for '${vector.name}'")
		}
	}
}
