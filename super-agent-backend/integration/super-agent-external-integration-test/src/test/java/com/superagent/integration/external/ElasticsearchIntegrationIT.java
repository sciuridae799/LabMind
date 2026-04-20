package com.superagent.integration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchIntegrationIT extends AbstractExternalIntegrationIT {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldIndexAndSearchDocumentInElasticsearch() throws Exception {
        String indexName = runId("es").replace("-", "");
        String documentId = runId("doc");
        String marker = runId("marker");

        try {
            send("PUT", "/" + indexName, """
                    {
                      "mappings": {
                        "properties": {
                          "marker": { "type": "keyword" }
                        }
                      }
                    }
                    """);

            send("PUT", "/" + indexName + "/_doc/" + documentId + "?refresh=true", """
                    {
                      "marker": "%s"
                    }
                    """.formatted(marker));

            JsonNode searchResponse = send("POST", "/" + indexName + "/_search", """
                    {
                      "query": {
                        "term": {
                          "marker": "%s"
                        }
                      }
                    }
                    """.formatted(marker));

            assertThat(searchResponse.at("/hits/total/value").asInt()).isEqualTo(1);
            assertThat(searchResponse.at("/hits/hits/0/_id").asText()).isEqualTo(documentId);
            assertThat(searchResponse.at("/hits/hits/0/_source/marker").asText()).isEqualTo(marker);
        } finally {
            sendForCleanup("DELETE", "/" + indexName);
        }
    }

    private JsonNode send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getElasticsearch().getEndpoint() + path))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json");

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        if (response.body() == null || response.body().isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.body());
    }

    private void sendForCleanup(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getElasticsearch().getEndpoint() + path))
                .timeout(HTTP_TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(200, 404);
    }
}
