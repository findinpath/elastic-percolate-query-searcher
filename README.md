Elastic Percolator Query Searcher
=================================

A common use case in working with Elasticsearch are the so called _"search alerts"_
which can be used (as [Google Alerts](https://www.google.com/alerts) puts it) to:

> Monitor the web for interesting new content


A [percolate](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-percolate-query.html) 
query can be executed to find out which registered search queries match the specified document 
for the query.

This functionality can be useful in case of dealing with alerts which require immediate
notification. For each queried document/ batch of documents, there will be issued one
notification for the matching registered search queries.

Assuming that the notifications for the search alerts should be batched so that the user gets notified
only after a certain time window (e.g.: hour, day, week) elapses 
(in case that the search alert matches at least one from the indexed documents during the time window), 
there should be retrieved the JSON search query corresponding to the alert and execute it against 
the search index so that the latest documents matching the search query to be retrieved and used in
the body of the user notification.

It takes quite some time to find on the Internet how to convert a JSON query in a
`org.elasticsearch.index.query.QueryBuilder` so that it can be used in a 
`org.elasticsearch.action.search.SearchRequest`.
This simple project deals exactly with this problem.

Converting a JSON query to a `org.elasticsearch.index.query.QueryBuilder`
can be done in the same fashion as in the 
[BoolQueryBuilderTests.java](https://github.com/elastic/elasticsearch/blob/master/server/src/test/java/org/elasticsearch/index/query/BoolQueryBuilderTests.java#L222).

By looking after the string [testFromJson()](https://github.com/elastic/elasticsearch/search?q=testFromJson%28%29&unscoped_q=testFromJson%28%29) in 
the [Elasticsearch](https://github.com/elastic/elasticsearch/) GitHub repository there can
be found plentifully examples on how to parse  a JSON string to a `org.elasticsearch.index.query.QueryBuilder`
instance.


## Parsing a JSON query to a `QueryBuilder`

```java

    private static final NamedXContentRegistry XCONTENT_REGISTRY = new NamedXContentRegistry(
            new SearchModule(
                    Settings.EMPTY,
                    false,
                    Collections.emptyList()
            ).getNamedXContents()
    );

    // ....

    private static QueryBuilder parseQuery(String queryAsString) throws IOException {
        var parser = JsonXContent.jsonXContent.createParser(
                XCONTENT_REGISTRY,
                LoggingDeprecationHandler.INSTANCE, queryAsString
        );
        return AbstractQueryBuilder.parseInnerQueryBuilder(parser);
    }

```


## Customize a parsed `QueryBuilder` from a JSON query

This project contains the [ElasticPercolateQuerySearcherTest](src/test/java/com/findinpath/elasticsearch/percolator/ElasticPercolateQuerySearcherTest.java) 
JUnit test class which:
 
- retrieves the details of a news alert
- parses the percolate query belonging to the news alert to a `org.elasticsearch.index.query.QueryBuilder`
- modifies the query builder by adding an extra filter (e.g. : only the documents published after a certain date)
- executes the query on the news index 


## Try out the tests

The tests on the project are based on [testcontainers](https://www.testcontainers.org/) library
for providing a lightweight, throwaway instance of [Elasticsearch](https://www.testcontainers.org/modules/elasticsearch/).
 
Run the tests on the project by executing:
`./gradlew test`