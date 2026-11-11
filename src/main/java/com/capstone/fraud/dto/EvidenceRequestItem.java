package com.capstone.fraud.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EvidenceRequestItem {
    private String item;
    private String reason;

    public EvidenceRequestItem() {}

    public EvidenceRequestItem(String item, String reason) {
        this.item = item;
        this.reason = reason;
    }
}
