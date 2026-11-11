package com.capstone.fraud.dto;

import com.capstone.fraud.domain.Channel;
import com.capstone.fraud.domain.DisputeType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CaseIntakeRequest {

    @NotBlank
    private String customerId;

    /** Last-4 only; full card numbers are never accepted or stored. */
    @NotBlank
    @Pattern(regexp = "\\d{4}", message = "cardLast4 must be exactly 4 digits")
    private String cardLast4;

    private String transactionRef;

    private String merchantName;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 3)
    private String currency = "USD";

    @NotNull
    private Channel channel;

    @NotNull
    private DisputeType disputeType;

    @Size(max = 4000)
    private String description;

    /** API flag: run the investigation immediately after intake. */
    private boolean investigateNow;
}
