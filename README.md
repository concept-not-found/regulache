Regulache
=========

##### Read through [mongoDB](http://www.mongodb.org/) cache of JSON web services for later processing

### Installation
Regulache is a [Groovy](http://groovy.codehaus.org/) [Maven](http://maven.apache.org/) project.

Maven dependency snippet

```xml
<dependency>
  <groupId>com.github.concept-not-found</groupId>
  <artifactId>regulache</artifactId>
  <version>1-SNAPSHOT</version>
</dependency>
```

[Groovy Grape](http://groovy.codehaus.org/Grape) snippet

    @Grab(group="com.github.concept-not-found", module="regulache", version="1-SNAPSHOT")

### Usage
Regulache is based off of [Groovy's RESTClient](http://groovy.codehaus.org/modules/http-builder/doc/rest.html)

    def regulache = new Regulache("http://base-url.com/context/", monogoDBCollection)
    def (json, cached) = regulache.executeGet(
      path: "web/service/path/with/{namedParameters}",
      "path-parameters": [
        namedParameters: "substitutePathParameters"
      ],
      "transient-queries": [
        api_key: "do_not_persist"
      ]
    )
    // this will make the request: GET http://base-url.com/context/web/service/path/with/substitutePathParameters?api_key=do_not_persist

Subsequent calls will always return the cached value unless `ignore-cache` or `ignore-cache-if-older-than` parameters are used.

On a successful GET request, the request is available in the provided mongoDB collection.

    mongodb> db.monogoDBCollection.findOne({base: "http://base-url.com/context/"})
    {
      "_id" : ObjectId("1234567890"),
      "headers" : {},
      "base" : "http://base-url.com/context/",
      "path" : "web/service/path/with/{namedParameters}",
      "path-parameters" : {
          "namedParameters" : "substitutePathParameters"
      },
      "queries" : {},
      "last-retrieved" : NumberLong("1387073116885"),
      "data" : {
        "the-original" : "json-body"
      }
    }

Full working example using League of Legends API: [example.groovy](https://github.com/concept-not-found/regulache/blob/master/example.groovy)

For more details, [read the source](https://github.com/concept-not-found/regulache/blob/master/src/main/groovy/com/github/concept/not/found/regulache/Regulache.groovy).

### Copyright and License
<pre>
Copyright 2013 Ronald Chen

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
</pre>
