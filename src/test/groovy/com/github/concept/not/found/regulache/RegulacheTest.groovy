package com.github.concept.not.found.regulache

import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner
import org.mockito.stubbing.OngoingStubbing

import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.mockito.Matchers.eq
import static org.mockito.Matchers.any
import static org.mockito.Mockito.never
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

@RunWith(MockitoJUnitRunner)
def class RegulacheTest {

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(8089);

	@Mock
	private DBCollection cache

	def regulache

	@Before
	void setUp() {
		regulache = new Regulache("http://localhost:8089", cache)
	}

	@Test(expected = HttpResponseException)
	void failOnNon200() {
		stubFor(get(urlEqualTo("/non-existent"))
				.willReturn(aResponse()
				.withStatus(404)))

		regulache.executeGet(
				path: "/non-existent"
		)
	}

	@Test(expected = IllegalStateException)
	void failOnNonJson() {
		stubFor(get(urlEqualTo("/non-json"))
				.willReturn(aResponse()
				.withHeader("Content-Type", ContentType.TEXT.toString())
				.withBody("Hello!")))

		regulache.executeGet(
				path: "/non-json"
		)
	}

	@Test
	void ensurePathParametersAreSubstituted() {
		stubFor(get(urlEqualTo("/api/na/summary/1234"))
				.willReturn(aResponse()
				.withHeader("Content-Type", ContentType.JSON.toString())
				.withBody("[true]")))

		def json = regulache.executeGet(
				path: "/api/{region}/summary/{userid}",
				"path-parameters": [
						region: "na",
						userid: "1234"
				]
		)

		assertEquals([true], json)
	}

	@Test
	void ensureRequestIsCached() {
		givenRequestIsNotPreviouslyCached()
		stubFor(get(urlEqualTo("/url-to-cache"))
				.willReturn(aResponse()
				.withHeader("Content-Type", ContentType.JSON.toString())
				.withBody('{"responseJson":42}')))

		regulache.executeGet(
				path: "/url-to-cache"
		)

		def keyCaptor = ArgumentCaptor.forClass(BasicDBObject)
		def valueCaptor = ArgumentCaptor.forClass(BasicDBObject)
		verify(cache).update(keyCaptor.capture(), valueCaptor.capture(), eq(true), eq(false))

		def key = keyCaptor.value as Map

		def expectedKey = [
				headers: [:],
				base: "http://localhost:8089/",
				path: "url-to-cache",
				"path-parameters": [:],
				queries: [:]
		]
		assertEquals(expectedKey, key)

		def value = valueCaptor.value as Map

		def keyValueIntersection = key.intersect(value)
		assertEquals("cache key should be a subset of cache value", expectedKey, keyValueIntersection)
		assertEquals([responseJson: 42], value.data)
		assertTrue(value.containsKey("last-retrieved"))
	}

	@Test
	void ensurePreviouslyCachedRequestsUseTheCache() {
		givenAPreviouslyCachedValue(System.currentTimeMillis())

		def json = regulache.executeGet(
				path: "/previously-cached"
		)

		verifyNoHttpRequestsAreMade()
		assertEquals(["cached-value"], json)
	}

	@Test
	void ensureIgnoreCacheDoesNotUseCachedValue() {
		givenAPreviouslyCachedValue(System.currentTimeMillis())

		stubFor(get(urlEqualTo("/should-ignore-cache"))
				.willReturn(aResponse()
				.withHeader("Content-Type", ContentType.JSON.toString())
				.withBody('["not from cache"]')))

		def json = regulache.executeGet(
				path: "/should-ignore-cache",
				"ignore-cache": true
		)

		verify(cache, never()).findOne((DBObject) any())
		assertEquals(["not from cache"], json)
	}

	@Test
	void ensureCacheValueIsNotUsedIfTooOld() {
		givenAPreviouslyCachedValue(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))

		stubFor(get(urlEqualTo("/should-ignore-cache"))
				.willReturn(aResponse()
				.withHeader("Content-Type", ContentType.JSON.toString())
				.withBody('["not from cache"]')))

		def json = regulache.executeGet(
				path: "/should-ignore-cache",
				"ignore-cache-if-older-than": TimeUnit.MINUTES.toMillis(30)
		)

		assertEquals(["not from cache"], json)
	}

	@Test
	void ensureCacheValueIsUsedIfFresh() {
		givenAPreviouslyCachedValue(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))

		def json = regulache.executeGet(
				path: "/previously-cached",
				"ignore-cache-if-older-than": TimeUnit.DAYS.toMillis(1)
		)

		verifyNoHttpRequestsAreMade()
		assertEquals(["cached-value"], json)
	}

	private OngoingStubbing<DBObject> givenAPreviouslyCachedValue(lastRetrieved) {
		when(cache.findOne((DBObject) any())).thenReturn(["last-retrieved": lastRetrieved, data: ["cached-value"]] as BasicDBObject)
	}

	def verifyNoHttpRequestsAreMade() {
		// current not possible to implement as a "verify"
		// will instead rely on wiremock to throw a HttpResponseException 404 on unstubbed calls
	}

	def givenRequestIsNotPreviouslyCached() {
		when(cache.findOne(any())).thenReturn(null)
	}
}
