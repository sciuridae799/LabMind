package com.labmind.business.chat.knowledge.retrieval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lab-mind.knowledge.retrieval")
public class KnowledgeRetrievalProperties {

    private int childTopK = 40;

    private int finalParentTopK = 6;

    private int maxParentChars = 2200;

    private int maxTotalEvidenceChars = 5200;

    private int maxSingleQuestionEvidenceChars = 2200;

    private Vector vector = new Vector();

    private Keyword keyword = new Keyword();

    private Rrf rrf = new Rrf();

    private Embedding embedding = new Embedding();

    private PgVector pgVector = new PgVector();

    private Elasticsearch elasticsearch = new Elasticsearch();

    private Rerank rerank = new Rerank();

    @Data
    public static class Vector {

        private int topK = 40;

        private double minSimilarity = 0.45D;
    }

    @Data
    public static class Keyword {

        private int topK = 40;

        private double relativeThreshold = 0.35D;
    }

    @Data
    public static class Rrf {

        private int k = 60;
    }

    @Data
    public static class Embedding {

        private String baseUrl;

        private String apiKey;

        private String model;
    }

    @Data
    public static class PgVector {

        private String url;

        private String username;

        private String password;
    }

    @Data
    public static class Elasticsearch {

        private String baseUrl;

        private String apiKey;

        private String username;

        private String password;

        private String indexName = "lab_mind_document_chunk";
    }

    @Data
    public static class Rerank {

        private boolean enabled;

        private String baseUrl;

        private String apiKey;

        private String model;
    }
}
