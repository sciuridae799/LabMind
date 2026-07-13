package com.labmind.common.web.database;

import java.util.List;
import java.util.Objects;

public class PageVo<T> {

    private final Long current;

    private final Long size;

    private final Long total;

    private final Long pages;

    private final List<T> records;

    public PageVo(Long current, Long size, Long total, Long pages, List<T> records) {
        this.current = Objects.requireNonNull(current, "current must not be null");
        this.size = Objects.requireNonNull(size, "size must not be null");
        this.total = Objects.requireNonNull(total, "total must not be null");
        this.pages = Objects.requireNonNull(pages, "pages must not be null");
        this.records = Objects.requireNonNull(records, "records must not be null");
    }

    public Long getCurrent() {
        return current;
    }

    public Long getSize() {
        return size;
    }

    public Long getTotal() {
        return total;
    }

    public Long getPages() {
        return pages;
    }

    public List<T> getRecords() {
        return records;
    }
}
