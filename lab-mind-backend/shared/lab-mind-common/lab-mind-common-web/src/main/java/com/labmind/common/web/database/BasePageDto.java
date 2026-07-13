package com.labmind.common.web.database;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class BasePageDto {

    @NotNull(message = "current must not be null")
    @Positive(message = "current must be greater than 0")
    private Long current;

    @NotNull(message = "size must not be null")
    @Positive(message = "size must be greater than 0")
    private Long size;

    public BasePageDto() {
    }

    public BasePageDto(Long current, Long size) {
        this.current = current;
        this.size = size;
    }

    public Long getCurrent() {
        return current;
    }

    public void setCurrent(Long current) {
        this.current = current;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
