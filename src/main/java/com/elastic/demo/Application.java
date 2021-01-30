package com.elastic.demo;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author qiaomengnan
 * @ClassName: Application
 * @Description:
 * @date 2021/1/29
 */
public class Application {

    public static RestHighLevelClient getClient() {

        HttpHost[] httpHosts = new HttpHost[1];
        httpHosts[0] = new HttpHost("127.0.0.1", 9200, "http");
        //final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        //credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

        RestClientBuilder builder = RestClient.builder(httpHosts).setRequestConfigCallback(requestConfigBuilder -> {
            requestConfigBuilder.setConnectTimeout(-1);
            requestConfigBuilder.setSocketTimeout(-1);
            requestConfigBuilder.setConnectionRequestTimeout(-1);
            return requestConfigBuilder;
        })
//                .setHttpClientConfigCallback(httpClientBuilder -> {
//            httpClientBuilder.disableAuthCaching();
//            return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
//        })
                .setMaxRetryTimeoutMillis(5 * 60 * 1000);

        return new RestHighLevelClient(builder);
    }


    /**
     * 新增，修改文档
     *
     * @param indexName 索引
     * @param type      mapping type
     * @param id        文档id
     * @param jsonStr   文档数据
     */
    public static int addData(RestHighLevelClient client , String indexName, String type, String id, String jsonStr) {
        int result = 1;
        try {
            // 1、创建索引请求  //索引  // type  //文档id
            IndexRequest request = new IndexRequest(indexName, type, id);
            // 2、准备文档数据 直接给JSON串
            request.source(jsonStr, XContentType.JSON);
            //4、发送请求
            IndexResponse indexResponse = null;
            indexResponse = client.index(request, RequestOptions.DEFAULT);
            //5、处理响应
            if (indexResponse != null) {
                String index1 = indexResponse.getIndex();
                String type1 = indexResponse.getType();
                String id1 = indexResponse.getId();
                long version1 = indexResponse.getVersion();
                long seqNo = indexResponse.getSeqNo();
                if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                    System.out.println("新增文档成功");
                } else if (indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                    System.out.println("修改文档成功!");
                }
                // 分片处理信息
                ReplicationResponse.ShardInfo shardInfo = indexResponse.getShardInfo();
                if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
                    System.out.println("分片处理信息,jsonStr=" + jsonStr);
                }
                // 如果有分片副本失败，可以获得失败原因信息
                if (shardInfo.getFailed() > 0) {
                    for (ReplicationResponse.ShardInfo.Failure failure : shardInfo.getFailures()) {
                        String reason = failure.reason();
                        System.out.println("副本失败原因：" + reason);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    public static long updateByQuery(RestHighLevelClient client ,String index, String type, String id, QueryBuilder queryBuilder, Map<String, Object> scriptMap, String idOrCode) {
        //执行的索引,相当于update table
        UpdateByQueryRequest updateRequest = new UpdateByQueryRequest(index);
        //执行脚本,相当于update set
        Script script = new Script(ScriptType.INLINE, "painless", idOrCode, scriptMap);
        updateRequest.setScript(script);
        //更新条件,相当于where
        updateRequest.setQuery(queryBuilder);
        updateRequest.setConflicts("proceed");
        updateRequest.setRefresh(true);
        try {
            //修改操作
            BulkByScrollResponse bulkResponse = client.updateByQuery(updateRequest, RequestOptions.DEFAULT);
            System.out.println(bulkResponse);
            if (bulkResponse.getVersionConflicts() > 0) {
                //如果更新为0，并且无冲突，则认为无效更新，返回1
                System.out.println("updateByQuery更新冲突大于1");
                return 1;
            }
            return 0;
        } catch (ElasticsearchException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }



    public static void main(String[] args) throws InterruptedException {
        RestHighLevelClient client = getClient();
        // System.out.println(addData(client, "hello_index", "hello_type","hello_id", "{\"data\": \"1\"}"));
        long result = 0 ;
        for (long i = 0; i < 10; i++) {
            System.out.println(i);
            result += update(client, i);
        }
        System.out.println(result);
        //update(client, 70);
    }

    public static long update(RestHighLevelClient client, long i) {
        Map<String,Object> data = new HashMap<>();
        data.put("data",i);
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        queryBuilder.must(QueryBuilders.rangeQuery("data").lte(i));
        return updateByQuery(client, "hello_index_2", "hello_type", "hello_id", queryBuilder, data,
                "ctx._source.data=params.data");
    }



}
