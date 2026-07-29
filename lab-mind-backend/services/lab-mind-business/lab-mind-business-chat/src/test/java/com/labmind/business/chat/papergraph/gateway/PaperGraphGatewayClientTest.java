package com.labmind.business.chat.papergraph.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.papergraph.config.PaperGraphServiceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperGraphGatewayClientTest {

    private HttpServer server;

    private PaperGraphGatewayClient client;

    private AtomicReference<String> requestHeaders;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requestHeaders = new AtomicReference<>();
        PaperGraphServiceProperties properties = new PaperGraphServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setInternalToken("internal-secret");
        client = new PaperGraphGatewayClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void listGraphsForwardsOnlyTrustedIdentityContext() {
        server.createContext("/api/paper-graphs", exchange -> {
            requestHeaders.set(String.join(
                    "|",
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("X-Lab-Mind-Internal-Token"),
                    exchange.getRequestHeaders().getFirst("X-Lab-Mind-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-Lab-Mind-Workspace-Id")));
            respond(exchange, 200, "[{\"id\":\"graph-1\",\"name\":\"Computer Papers\"}]");
        });
        server.start();

        JsonNode response = client.listGraphs(
                new PaperGraphGatewayContext("42", "workspace-1"));

        assertEquals("GET|internal-secret|42|workspace-1", requestHeaders.get());
        assertEquals("Computer Papers", response.get(0).get("name").asText());
    }

    @Test
    void propagatesPythonConflictWithExplicitDetail() {
        server.createContext("/api/paper-graphs", exchange -> respond(
                exchange,
                409,
                "{\"detail\":\"paper graph cannot be deleted while a document is being built\"}"));
        server.start();

        PaperGraphGatewayException error = assertThrows(
                PaperGraphGatewayException.class,
                () -> client.listGraphs(new PaperGraphGatewayContext("42", "workspace-1")));

        assertEquals(409, error.getStatusCode().value());
        assertEquals(
                "paper graph cannot be deleted while a document is being built",
                error.getMessage());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }
}
