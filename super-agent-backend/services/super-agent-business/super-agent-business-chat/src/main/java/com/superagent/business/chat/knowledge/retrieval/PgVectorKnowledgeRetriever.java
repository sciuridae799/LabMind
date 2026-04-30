package com.superagent.business.chat.knowledge.retrieval;

import com.superagent.business.chat.knowledge.indexing.KnowledgeRetrievalIndexChunk;
import com.superagent.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PgVectorKnowledgeRetriever {

    private static final int NORMAL_STATUS = 1;

    private final KnowledgeRetrievalProperties retrievalProperties;

    public void upsert(KnowledgeRetrievalIndexChunk chunk, List<Double> embedding) {
        String sql = """
                INSERT INTO public.super_agent_document_embedding (
                    id, document_id, task_id, plan_id, parent_block_id, chunk_no, source_type,
                    section_path, structure_node_id, structure_node_type, canonical_path, item_index,
                    chunk_text, char_count, token_count, embedding_model, metadata_json, embedding, status,
                    create_time, edit_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    task_id = EXCLUDED.task_id,
                    plan_id = EXCLUDED.plan_id,
                    parent_block_id = EXCLUDED.parent_block_id,
                    chunk_no = EXCLUDED.chunk_no,
                    source_type = EXCLUDED.source_type,
                    section_path = EXCLUDED.section_path,
                    structure_node_id = EXCLUDED.structure_node_id,
                    structure_node_type = EXCLUDED.structure_node_type,
                    canonical_path = EXCLUDED.canonical_path,
                    item_index = EXCLUDED.item_index,
                    chunk_text = EXCLUDED.chunk_text,
                    char_count = EXCLUDED.char_count,
                    token_count = EXCLUDED.token_count,
                    embedding_model = EXCLUDED.embedding_model,
                    metadata_json = EXCLUDED.metadata_json,
                    embedding = EXCLUDED.embedding,
                    status = EXCLUDED.status,
                    edit_time = now()
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chunk.chunkId());
            statement.setLong(2, chunk.documentId());
            statement.setLong(3, chunk.taskId());
            setNullableLong(statement, 4, chunk.planId());
            statement.setLong(5, chunk.parentBlockId());
            statement.setInt(6, chunk.chunkNo());
            statement.setInt(7, chunk.sourceType());
            statement.setString(8, chunk.sectionPath());
            setNullableLong(statement, 9, chunk.structureNodeId());
            setNullableInteger(statement, 10, chunk.structureNodeType());
            statement.setString(11, chunk.canonicalPath());
            setNullableInteger(statement, 12, chunk.itemIndex());
            statement.setString(13, chunk.chunkText());
            statement.setInt(14, chunk.charCount());
            statement.setInt(15, chunk.tokenCount());
            statement.setString(16, retrievalProperties.getEmbedding().getModel());
            statement.setString(17, chunk.metadataJson());
            statement.setString(18, vectorLiteral(embedding));
            statement.setInt(19, NORMAL_STATUS);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upsert PGVector embedding for chunkId=" + chunk.chunkId(), exception);
        }
    }

    public void deleteByDocumentId(long documentId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM public.super_agent_document_embedding WHERE document_id = ?")) {
            statement.setLong(1, documentId);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete PGVector embeddings for documentId=" + documentId, exception);
        }
    }

    public List<KnowledgeRetrievalChildHit> search(
            String question,
            List<Long> documentIdList,
            List<Double> queryEmbedding) {
        if (documentIdList == null || documentIdList.isEmpty()) {
            return List.of();
        }
        String placeholders = documentIdList.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT id, document_id, parent_block_id, chunk_no, section_path, chunk_text,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM public.super_agent_document_embedding
                WHERE status = 1
                  AND document_id IN (%s)
                  AND 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            String vector = vectorLiteral(queryEmbedding);
            statement.setString(parameterIndex++, vector);
            for (Long documentId : documentIdList) {
                statement.setLong(parameterIndex++, documentId);
            }
            statement.setString(parameterIndex++, vector);
            statement.setDouble(parameterIndex++, retrievalProperties.getVector().getMinSimilarity());
            statement.setString(parameterIndex++, vector);
            statement.setInt(parameterIndex, retrievalProperties.getVector().getTopK());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<KnowledgeRetrievalChildHit> hitList = new ArrayList<>();
                int rank = 1;
                while (resultSet.next()) {
                    hitList.add(new KnowledgeRetrievalChildHit(
                            resultSet.getLong("id"),
                            resultSet.getLong("document_id"),
                            resultSet.getLong("parent_block_id"),
                            resultSet.getInt("chunk_no"),
                            null,
                            resultSet.getString("section_path"),
                            resultSet.getString("chunk_text"),
                            "VECTOR",
                            resultSet.getDouble("similarity"),
                            rank++));
                }
                return hitList;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to search PGVector knowledge chunks.", exception);
        }
    }

    private Connection openConnection() throws Exception {
        KnowledgeRetrievalProperties.PgVector pgVector = retrievalProperties.getPgVector();
        requireText(pgVector.getUrl(), "retrieval pgVector url");
        requireText(pgVector.getUsername(), "retrieval pgVector username");
        requireText(pgVector.getPassword(), "retrieval pgVector password");
        return DriverManager.getConnection(pgVector.getUrl(), pgVector.getUsername(), pgVector.getPassword());
    }

    private String vectorLiteral(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("embedding vector is empty.");
        }
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws Exception {
        if (value == null) {
            statement.setObject(index, null);
            return;
        }
        statement.setLong(index, value);
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws Exception {
        if (value == null) {
            statement.setObject(index, null);
            return;
        }
        statement.setInt(index, value);
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(fieldName + " is required.");
        }
    }
}
