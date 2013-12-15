@Grapes([
	@Grab(group="org.mongodb", module="mongo-java-driver", version="2.9.3"),
	@Grab(group="com.github.concept-not-found", module="regulache", version="1-SNAPSHOT"),
	@Grab(group="org.codehaus.groovy.modules.http-builder", module="http-builder", version="0.6")
])
import com.mongodb.*
import com.github.concept.not.found.regulache.Regulache
import groovyx.net.http.HttpResponseException

def getApiKey() {
	def lol_api_key = System.getenv("lol_api_key")

	if (!lol_api_key) {
		throw new IllegalArgumentException("missing lol_api_key property")
	}

	lol_api_key
}

def mongo = new Mongo()

try {
	def db = mongo.getDB("live")
	def lolapi = db.getCollection("lolapi")

	def regulache = new Regulache("https://prod.api.pvp.net/", lolapi)
	def id = findSummonerIdByName(regulache, "WildTurtle")
	println("WildTurtle's summoner id is $id")
} finally {
	mongo.close()
}

def findSummonerIdByName(regulache, name) {
	try {
		def json = regulache.executeGet(
				path: "/api/lol/{region}/v1.1/summoner/by-name/{name}",
				"path-parameters": [
						region: "na",
						name: name
				],
				"transient-queries": [
						api_key: getApiKey()
				]
		)
		json.id
	} catch (HttpResponseException e) {
		println("failed to fetch $name")
		e.printStackTrace()
		null
	}
}

