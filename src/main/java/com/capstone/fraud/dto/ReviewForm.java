package com.capstone.fraud.dto;

import com.capstone.fraud.domain.Disposition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewForm {

    @NotNull
    private Disposition disposition;

    /** A rationale is mandatory: the human decision is the audit anchor. */
    @NotBlank
    @Size(min = 10, max = 4000)
    private String rationale;
}
