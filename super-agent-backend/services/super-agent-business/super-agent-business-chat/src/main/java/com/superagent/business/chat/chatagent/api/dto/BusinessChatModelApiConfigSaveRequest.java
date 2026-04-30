package com.superagent.business.chat.chatagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BusinessChatModelApiConfigSaveRequest {

    private String id;

    @NotBlank(message = "provider must not be blank")
    @Size(max = 32, message = "provider length must be less than or equal to 32")
    private String provider;

    @NotBlank(message = "displayName must not be blank")
    @Size(max = 64, message = "displayName length must be less than or equal to 64")
    private String displayName;

    @NotBlank(message = "baseUrl must not be blank")
    @Size(max = 255, message = "baseUrl length must be less than or equal to 255")
    private String baseUrl;

    @NotBlank(message = "modelName must not be blank")
    @Size(max = 128, message = "modelName length must be less than or equal to 128")
    private String modelName;

    @Size(max = 512, message = "apiKey length must be less than or equal to 512")
    private String apiKey;

    private BigDecimal inputTokenUnitPrice;

    private BigDecimal outputTokenUnitPrice;

    @Min(value = 1, message = "priceUnitTokens must be greater than or equal to 1")
    private Integer priceUnitTokens;

    @Size(max = 16, message = "currency length must be less than or equal to 16")
    private String currency;

    private Boolean enabled;
}
