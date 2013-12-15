package com.github.concept.not.found.regulache

import com.mongodb.BasicDBObject
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient

def class Regulache {

	def base
	def client
	def cache

	def Regulache(base, cache) {
		this.base = addTrailingSlashIfMissing(base)
		client = new RESTClient(this.base)
		this.cache = cache
	}

	/**
	 * Makes a web service get for json content and caches successful result.
	 *
	 * Subsequent calls will always return cached value unless ignore-cache parameters are used.  If cached is ignored,
	 * the cache will be updated with the next successful result.
	 *
	 * Required parameters:
	 * <ul>
	 *     <li>path - the path to be appended to the base to form the full url.  can contain path parameters surrounded by {}</li>
	 * </ul>
	 * Optional parameters:
	 * <ul>
	 *     <li>path-parameters - map of path parameters to values</li>
	 *     <li>headers - headers to include with request</li>
	 *     <li>transient-headers - headers to include with request, but won't be persisted</li>
	 *     <li>queries - map of query parameters to include with request</li>
	 *     <li>transient-queries - map of query parameters to include with request, but won't be persisted</li>
	 *     <li>ignore-cache - boolean that indicates if cache value should be used or not.</li>
	 *     <li>ignore-cache-if-older-than - ignores cached value if it is older than the given value in milliseconds</li>
	 * </ul>
	 *
	 * It would have been ideal to use the parameter "query" instead of "queries" to match up with Groovy's rest client
	 * but due to a MongoDB bug https://jira.mongodb.org/browse/SERVER-9812 using the field "query" causes problems when
	 * using find().
	 *
	 * @param parameterMap the parameters
	 * @throws HttpResponseException on non 200 status
	 * @throws IllegalStateException if response content type is not json, cause includes HttpResponseException
	 * @return the json response
	 */
	def executeGet(parameterMap) {
		def headers = [:]
		headers += parameterMap["headers"] ?: [:]

		def path = convertToRelativePath(parameterMap["path"])
		def pathParameters = parameterMap["path-parameters"] ?: [:]

		def queries = [:]
		queries += parameterMap["queries"] ?: [:]

		def cacheKey = [
				headers: headers.sort(),
				// do not record transient-headers
				base: base,
				path: path,
				"path-parameters": pathParameters.sort(),
				queries: queries.sort()
				// do not record transient-queries
		]

		def cacheValue
		def ignoreCache = parameterMap["ignore-cache"] ?: false
		if (!ignoreCache) {
			cacheValue = cache.findOne(cacheKey as BasicDBObject)
			def ignoreCacheIfOlderThan = parameterMap["ignore-cache-if-older-than"] ?: Long.MAX_VALUE
			if (cacheValue != null) {
				def cacheValueAge = System.currentTimeMillis() - cacheValue["last-retrieved"]
				if (cacheValueAge < ignoreCacheIfOlderThan) {
					return cacheValue.data
				}
			}
		}

		def transientHeaders = parameterMap["transient-headers"] ?: [:]
		def transientQueries = parameterMap["transient-queries"] ?: [:]
		def response = client.get(
				path:  populatePathParameters(path, pathParameters),
				headers: headers + transientHeaders,
				query: queries + transientQueries)
		if (response.contentType != ContentType.JSON.toString()) {
			throw new IllegalStateException("expected json Content-Type, but was $response.contentType",
					new HttpResponseException(response))
		}
		def json = response.data

		cacheValue = cacheKey.clone()
		cacheValue["last-retrieved"] = System.currentTimeMillis()
		cacheValue.data = json
		cache.update(
				cacheKey as BasicDBObject,
				cacheValue as BasicDBObject,
				true,
				false
		)

		json
	}

	def populatePathParameters(path, pathParameters) {
		def result = path
		pathParameters.each {
			key, value ->
				result = result.replace("{$key}", value)
		}
		result
	}

	def convertToRelativePath(uri) {
		uri.replaceFirst("^/", "")
	}

	def addTrailingSlashIfMissing(uri) {
		def result = uri
		if (!result.endsWith("/")) {
			result += "/"
		}
		result
	}
}
