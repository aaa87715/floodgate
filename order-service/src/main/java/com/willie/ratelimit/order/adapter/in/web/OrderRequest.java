
package com.willie.ratelimit.order.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderRequest(
        @NotBlank String item,
        @Positive int quantity
) {}