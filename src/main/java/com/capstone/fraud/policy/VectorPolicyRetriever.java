package com.capstone.fraud.policy;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.PolicyChunk;
import com.capstone.fraud.repo.PolicyChunkRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Policy retriever backed by a LangChain4j {@link InMemoryEmbeddingStore}.
 * Chunks and queries are embedded with {@link PolicyEmbeddingModel} —
 * deterministic dense TF-IDF vectors over the corpus vocabulary — and
 * ranked by the store's cosine-similarity search, so retrieval is
 * reproducible offline and identical in tests. Only ACTIVE policy
 * chunks are indexed; RETIRED chunks are excluded at index time.
 * Retrieval below the configured minimum score is reported as
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
    private Map<String, PolicyChunk> chunksById = Map.of();
    private PolicyEmbeddingModel embeddingModel;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;

    public VectorPolicyRetriever(PolicyChunkRepository chunkRepo, FraudProperties properties) {
        this.chunkRepo = chunkRepo;
        this.minScore = properties.getRetrieval().getMinScore();
    }

    @Override
    public RetrievalResult retrieve(String query, int topK) {
        ensureIndexed();
        if (chunksById.isEmpty() || embeddingModel.knownTermCount(query) == 0) {
            return new RetrievalResult(List.of(), false, 0.0);
        }
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .minScore(0.0)
                        .build()).matches();

        List<RetrievedPassage> scored = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> m : matches) {
            String chunkId = m.embedded().metadata().getString("chunkKey");
            PolicyChunk chunk = chunksById.get(chunkId);
            if (chunk != null && m.score() > 0) {
                scored.add(new RetrievedPassage(chunk, m.score()));
            }
        }
        scored.sort(Comparator.comparingDouble(RetrievedPassage::getScore).reversed());
        double max = scored.isEmpty() ? 0.0 : scored.get(0).getScore();
        return new RetrievalResult(scored, max >= minScore, max);
    }

    /** Visible for tests. */
    public synchronized void index() {
        List<PolicyChunk> active = chunkRepo.findAll().stream()
                .filter(this::isActive)
                .toList();
        Map<String, PolicyChunk> byId = new HashMap<>();
        Map<String, Integer> vocabIndex = new HashMap<>();
        Map<String, Integer> df = new HashMap<>();
        for (PolicyChunk c : active) {
            byId.put(chunkKey(c), c);
            List<String> tokens = tokenize(c.getText() + " " + c.getSection() + " " + c.getPolicyTitle());
            new HashSet<>(tokens).forEach(t -> {
                df.merge(t, 1, Integer::sum);
                vocabIndex.putIfAbsent(t, vocabIndex.size());
            });
        }
        int n = Math.max(1, active.size());
        Map<String, Double> idfMap = new HashMap<>();
        df.forEach((term, count) -> idfMap.put(term, Math.log(1.0 + (double) n / count)));

        this.embeddingModel = new PolicyEmbeddingModel(vocabIndex, idfMap);
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        for (PolicyChunk c : active) {
            TextSegment segment = TextSegment.from(
                    c.getText() + " " + c.getSection() + " " + c.getPolicyTitle(),
                    new Metadata().put("chunkKey", chunkKey(c)));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
        this.chunksById = byId;
        this.indexed = true;
        log.info("Policy embedding store built: {} active chunks, {} terms",
                active.size(), vocabIndex.size());
    }

    private void ensureIndexed() {
        if (!indexed) {
            synchronized (this) {
                if (!indexed) index();
            }
        }
    }

    private String chunkKey(PolicyChunk c) {
        return c.getId() != null ? c.getId()
                : c.getPolicyId() + "#" + c.getChunkIndex() + "#" + Objects.hashCode(c.getSection());
    }

    private boolean isActive(PolicyChunk c) {
        return c.getStatus() == null || "ACTIVE".equalsIgnoreCase(c.getStatus());
    }

    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() > 1 && !STOPWORDS.contains(raw)) out.add(raw);
        }
        return out;
    }
}
