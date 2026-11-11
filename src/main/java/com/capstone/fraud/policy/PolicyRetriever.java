package com.capstone.fraud.policy;

public interface PolicyRetriever {
    RetrievalResult retrieve(String query, int topK);
}
