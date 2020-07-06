package com.findinpath.elasticsearch.percolator;

import com.findinpath.elasticsearch.model.News;
import com.findinpath.elasticsearch.model.NewsAlert;
import org.apache.http.HttpHost;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;

import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
public class ElasticPercolateQuerySearcherTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticPercolateQuerySearcherTest.class);

    /**
     * Elasticsearch version which should be used for the Tests
     */
    private static final String ELASTICSEARCH_VERSION = Version.CURRENT.toString();

    @Container
    private static ElasticsearchContainer ELASTICSEARCH_CONTAINER =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:" + ELASTICSEARCH_VERSION);


    private static final NamedXContentRegistry XCONTENT_REGISTRY = new NamedXContentRegistry(
            new SearchModule(
                    Settings.EMPTY,
                    false,
                    Collections.emptyList()
            ).getNamedXContents()
    );

    private static final String NEWS_INDEX = "news";
    private static final String NEWS_ALERT_INDEX = "news-alert";


    @BeforeEach
    public void setup() throws Exception {
        try (var client = getClient(ELASTICSEARCH_CONTAINER)) {
            createNewsIndex(client);
            createNewsAlertIndex(client);
        }
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void demo() throws IOException {
        var news = new News(
                "1",
                "Early snow this year",
                "After a year with hardly any snow, this is going to be a serious winter",
                "weather",
                Instant.now().minus(10, ChronoUnit.MINUTES)
        );

        var lastSearchedNewsAlertPublishedDate = Instant.now().minus(1, ChronoUnit.HOURS);

        var newsAlertQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("title", "snow"))
                .filter(QueryBuilders.termQuery("category", "weather"));

        var newsAlert = new NewsAlert("100", "news about snow", "contact@findinpath.com", null);


        try (var client = getClient(ELASTICSEARCH_CONTAINER)) {
            // Index news alert document
            indexNewsAlertDocument(client, newsAlert, newsAlertQuery);
            // Index news document that corresponds to the previously registered news alert
            indexNewsDocument(client, news);
        }

        try (var client = getClient(ELASTICSEARCH_CONTAINER)) {

            // ....
            // Assume that after indexing the document a callback is triggered
            // in order to send email notifications corresponding to the latest news
            // corresponding for the news alert.
            // ...


            // Retrieve the news alert from Elasticsearch
            LOGGER.info("Retrieve the news alert details for the ID " + newsAlert.getId() + " from the index " + NEWS_ALERT_INDEX);
            var newsAlertGetRequest = new GetRequest(NEWS_ALERT_INDEX, newsAlert.getId());
            var newsAlertGetResponse = client.get(newsAlertGetRequest, RequestOptions.DEFAULT);
            if (!newsAlertGetResponse.isExists()) {
                fail("The news alert " + newsAlert.getId() + " should be present in the " + NEWS_ALERT_INDEX + " index");
            }

            var readNewsAlert = createNewsAlert(newsAlertGetResponse);

            // Retrieve the most actual news for the news alert configured query
            // by using the query of the news alert on the NEWS_INDEX search index.
            var searchAgentQueryBuilder = parseQuery(readNewsAlert.getQuery());

            // Add published date filter with latest search date retrieved from an external source (e.g. : an ACID database)
            // in order to retrieve only the latest matches for the news alert.
            // We assume that all the news alerts are configured as boolean queries
            // see https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-bool-query.html
            ((BoolQueryBuilder) searchAgentQueryBuilder)
                    .filter(rangeQuery("published_date").gt(Date.from(lastSearchedNewsAlertPublishedDate)));

            var newsSearchRequest = new SearchRequest(NEWS_INDEX);
            var newsSourceBuilder = new SearchSourceBuilder();
            newsSourceBuilder.query(searchAgentQueryBuilder);
            // retrieve the latest news
            newsSourceBuilder.sort(new FieldSortBuilder("published_date").order(SortOrder.DESC));


            LOGGER.info("Executing the search request " + newsSearchRequest + " on the index " + NEWS_INDEX);
            var newsResponse = client.search(newsSearchRequest, RequestOptions.DEFAULT);

            assertThat(newsResponse.getHits().getTotalHits().value, equalTo(1L));

            var foundNews = createNews(newsResponse.getHits().getHits()[0]);

            assertThat(foundNews, equalTo(news));
        }


    }


    private RestHighLevelClient getClient(ElasticsearchContainer container) {
        return new RestHighLevelClient(RestClient.builder(HttpHost.create(container.getHttpHostAddress())));
    }


    private News createNews(SearchHit searchHit) {
        var source = searchHit.getSourceAsString();
        var sourceJson = new JSONObject(source);
        var title = sourceJson.get("title").toString();
        var body = sourceJson.get("body").toString();
        var category = sourceJson.get("category").toString();
        var publishedDate = Instant.parse(sourceJson.get("published_date").toString());
        return new News(searchHit.getId(), title, body, category, publishedDate);
    }

    private NewsAlert createNewsAlert(GetResponse newsAlertGetResponse) {
        var source = newsAlertGetResponse.getSourceAsString();
        var sourceJson = new JSONObject(source);
        var name = sourceJson.get("name").toString();
        var email = sourceJson.get("email").toString();
        var query = sourceJson.get("query").toString();

        return new NewsAlert(newsAlertGetResponse.getId(), name, email, query);
    }

    private void indexNewsDocument(
            RestHighLevelClient client,
            News news
    ) throws IOException {
        LOGGER.info("Indexing the " + news + " document on the index " + NEWS_INDEX);

        var sourcebuilder = XContentFactory.jsonBuilder();
        sourcebuilder.startObject();
        {
            sourcebuilder.field("title", news.getTitle());
            sourcebuilder.field("body", news.getBody());
            sourcebuilder.field("category", news.getCategory());
            sourcebuilder.field("published_date", news.getPublishedDate());
        }
        sourcebuilder.endObject();
        var request = new IndexRequest(NEWS_INDEX)
                .id(news.getId())
                .source(sourcebuilder)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client.index(request, RequestOptions.DEFAULT);
    }


    private void indexNewsAlertDocument(
            RestHighLevelClient client,
            NewsAlert newsAlert,
            QueryBuilder queryBuilder
    ) throws IOException {

        LOGGER.info("Indexing the " + newsAlert + " document on the index " + NEWS_ALERT_INDEX);
        var sourcebuilder = XContentFactory.jsonBuilder();
        sourcebuilder.startObject();
        {
            sourcebuilder.field("name", newsAlert.getName());
            sourcebuilder.field("email", newsAlert.getEmail());
            sourcebuilder.field("query", queryBuilder);
        }
        sourcebuilder.endObject();
        var request = new IndexRequest(NEWS_ALERT_INDEX)
                .id(newsAlert.getId())
                .source(sourcebuilder)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client.index(request, RequestOptions.DEFAULT);
    }

    private void createNewsIndex(RestHighLevelClient client) throws IOException {
        if (client.indices().exists(new GetIndexRequest(NEWS_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(NEWS_INDEX), RequestOptions.DEFAULT);
        }
        var createNewsIndexRequest = new CreateIndexRequest(NEWS_INDEX);
        var newsMappingsbuilder = XContentFactory.jsonBuilder();
        newsMappingsbuilder.startObject();
        {
            newsMappingsbuilder.startObject("properties");
            {
                newsMappingsbuilder.startObject("title");
                {
                    newsMappingsbuilder.field("type", "text");
                }
                newsMappingsbuilder.endObject();

                newsMappingsbuilder.startObject("body");
                {
                    newsMappingsbuilder.field("type", "text");
                }
                newsMappingsbuilder.endObject();

                newsMappingsbuilder.startObject("category");
                {
                    newsMappingsbuilder.field("type", "keyword");
                }
                newsMappingsbuilder.endObject();

            }
            newsMappingsbuilder.endObject();
        }
        newsMappingsbuilder.endObject();


        createNewsIndexRequest.mapping(newsMappingsbuilder);
        client.indices().create(createNewsIndexRequest, RequestOptions.DEFAULT);
    }


    private void createNewsAlertIndex(RestHighLevelClient client) throws IOException {
        if (client.indices().exists(new GetIndexRequest(NEWS_ALERT_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(NEWS_ALERT_INDEX), RequestOptions.DEFAULT);
        }
        var createNewsNotifyIndexRequest = new CreateIndexRequest(NEWS_ALERT_INDEX);
        var newsNotifyMappingsbuilder = XContentFactory.jsonBuilder();
        newsNotifyMappingsbuilder.startObject();
        {
            newsNotifyMappingsbuilder.startObject("properties");
            {

                newsNotifyMappingsbuilder.startObject("title");
                {
                    newsNotifyMappingsbuilder.field("type", "text");
                }
                newsNotifyMappingsbuilder.endObject();

                newsNotifyMappingsbuilder.startObject("category");
                {
                    newsNotifyMappingsbuilder.field("type", "keyword");
                }
                newsNotifyMappingsbuilder.endObject();

                newsNotifyMappingsbuilder.startObject("name");
                {
                    newsNotifyMappingsbuilder.field("type", "text");
                }
                newsNotifyMappingsbuilder.endObject();

                newsNotifyMappingsbuilder.startObject("email");
                {
                    newsNotifyMappingsbuilder.field("type", "text");
                }
                newsNotifyMappingsbuilder.endObject();

                newsNotifyMappingsbuilder.startObject("query");
                {
                    newsNotifyMappingsbuilder.field("type", "percolator");
                }
                newsNotifyMappingsbuilder.endObject();
            }
            newsNotifyMappingsbuilder.endObject();
        }
        newsNotifyMappingsbuilder.endObject();

        createNewsNotifyIndexRequest.mapping(newsNotifyMappingsbuilder);
        client.indices().create(createNewsNotifyIndexRequest, RequestOptions.DEFAULT);
    }


    private static QueryBuilder parseQuery(String queryAsString) throws IOException {
        var parser = JsonXContent.jsonXContent.createParser(
                XCONTENT_REGISTRY,
                LoggingDeprecationHandler.INSTANCE, queryAsString
        );
        return AbstractQueryBuilder.parseInnerQueryBuilder(parser);
    }
}
