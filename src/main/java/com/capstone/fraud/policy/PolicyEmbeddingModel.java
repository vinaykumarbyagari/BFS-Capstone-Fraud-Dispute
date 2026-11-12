package com.capstone.fraud.policy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic local embedding model: embeds text into a fixed-size
 * dense TF-IDF vector over a frozen corpus vocabulary. No external
 * embedding service or model download — embeddings are reproducible
 * offline and identical across runs and tests, while still flowing
 * through the standard LangChain4j {@link EmbeddingModel} /
 * {@code EmbeddingStore} interfaces.
 */
public class PolicyEmbeddingModel implements EmbeddingModel {

    private final Map<String, Integer> vocabIndex;
    private final Map<String, Double> idf;
    private final int dimensions;

    public PolicyEmbeddingModel(Map<String, Integer> vocabIndex, Map<String, Double> idf) {
        this.vocabIndex = vocabIndex;
        this.idf = idf;
        this.dimensions = Math.max(1, vocabIndex.size());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> out = new ArrayList<>(segments.size());
        for (TextSegment s : segments) {
            out.add(new Embedding(vectorize(s.text())));
        }
        return Response.from(out);
    }

    @Override
    public int dimension() {
        return dimensions;
    }

    /** Number of query tokens present in the corpus vocabulary — 0 means the query cannot match. */
    public int knownTermCount(String text) {
        return (int) VectorPolicyRetriever.tokenize(text).stream()
                .filter(vocabIndex::containsKey).count();
    }

    private float[] vectorize(String text) {
        float[] vec = new float[dimensions];
        List<String> tokens = VectorPolicyRetriever.tokenize(text);
        Map<String, Integer> tf = new java.util.HashMap<>();
        tokens.forEach(t -> tf.merge(t, 1, Integer::sum));
        double maxTf = tf.values().stream().mapToInt(i -> i).max().orElse(1);
        tf.forEach((term, count) -> {
            Integer idx = vocabIndex.get(term);
            if (idx != null) {
                double idfVal = idf.getOrDefault(term, 0.0);
                vec[idx] = (float) ((0.5 + 0.5 * count / maxTf) * idfVal);
            }
        });
        return vec;
    }
}
