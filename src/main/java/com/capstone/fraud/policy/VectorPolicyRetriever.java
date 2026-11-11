package com.capstone.fraud.policy;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.PolicyChunk;
import com.capstone.fraud.repo.PolicyChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Deterministic in-memory vector retriever. Chunks and queries are
 * embedded as TF-IDF vectors (term-frequency × inverse-document-frequency)
 * and ranked by cosine similarity — no external embedding service needed,
 * which keeps policy retrieval reproducible offline and identical in
 * tests. Retrieval below the configured minimum score is reported as
 * insufficient so callers can abstain.
 *
 * The index is built lazily on first use so it cannot race policy
 * ingestion during startup.
 */
@Slf4j
@Service
public class VectorPolicyRetriever implements PolicyRetriever {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "for", "on", "at", "is", "are",
            "be", "by", "with", "as", "that", "this", "it", "its", "from", "not", "must",
            "shall", "may", "will", "if", "when", "than", "then", "any", "all", "each");

    private final PolicyChunkRepository chunkRepo;
    private final double minScore;

    private volatile boolean indexed;
    private List<PolicyChunk> chunks = List.of();
    private List<Map<String, Double>> vectors = List.of();
    private Map<String, Double> idf = Map.of();

    public VectorPolicyRetriever(PolicyChunkRepository chunkRepo, FraudProperties properties) {
        this.chunkRepo = chunkRepo;
        this.minScore = properties.getRetrieval().getMinScore();
    }

    @Override
    public RetrievalResult retrieve(String query, int topK) {
        ensureIndexed();
        if (chunks.isEmpty()) {
            return new RetrievalResult(List.of(), false, 0.0);
        }
        Map<String, Double> qv = tfIdf(tokenize(query), idf);
        List<RetrievedPassage> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double score = cosine(qv, vectors.get(i));
            if (score > 0) {
                scored.add(new RetrievedPassage(chunks.get(i), score));
            }
        }
        scored.sort(Comparator.comparingDouble(RetrievedPassage::getScore).reversed());
        List<RetrievedPassage> top = scored.stream().limit(topK).toList();
        double max = top.isEmpty() ? 0.0 : top.get(0).getScore();
        return new RetrievalResult(top, max >= minScore, max);
    }

    /** Visible for tests. */
    public synchronized void index() {
        chunks = chunkRepo.findAll();
        Map<String, Integer> df = new HashMap<>();
        List<List<String>> tokenized = new ArrayList<>();
        for (PolicyChunk c : chunks) {
            List<String> tokens = tokenize(c.getText() + " " + c.getSection() + " " + c.getPolicyTitle());
            tokenized.add(tokens);
            new HashSet<>(tokens).forEach(t -> df.merge(t, 1, Integer::sum));
        }
        int n = Math.max(1, chunks.size());
        Map<String, Double> idfMap = new HashMap<>();
        df.forEach((term, count) -> idfMap.put(term, Math.log(1.0 + (double) n / count)));
        this.idf = idfMap;

        List<Map<String, Double>> vecs = new ArrayList<>();
        for (List<String> tokens : tokenized) {
            vecs.add(tfIdf(tokens, idfMap));
        }
        this.vectors = vecs;
        this.indexed = true;
        log.info("Policy vector index built: {} chunks, {} terms", chunks.size(), idfMap.size());
    }

    private void ensureIndexed() {
        if (!indexed) {
            synchronized (this) {
                if (!indexed) index();
            }
        }
    }

    private Map<String, Double> tfIdf(List<String> tokens, Map<String, Double> idfMap) {
        Map<String, Double> vec = new HashMap<>();
        Map<String, Integer> tf = new HashMap<>();
        tokens.forEach(t -> tf.merge(t, 1, Integer::sum));
        double maxTf = tf.values().stream().mapToInt(i -> i).max().orElse(1);
        tf.forEach((term, count) -> {
            double idfVal = idfMap.getOrDefault(term, Math.log(1.0 + chunks.size() / 1.0));
            vec.put(term, (0.5 + 0.5 * count / maxTf) * idfVal);
        });
        return vec;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0, na = 0, nb = 0;
        for (var e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Double bv = b.get(e.getKey());
            if (bv != null) dot += e.getValue() * bv;
        }
        for (double v : b.values()) nb += v * v;
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() > 1 && !STOPWORDS.contains(raw)) out.add(raw);
        }
        return out;
    }
}
