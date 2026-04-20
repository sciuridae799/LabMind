package com.superagent.common.web.database;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class PageUtil {

    private PageUtil() {
    }

    public static <T> Page<T> toPage(BasePageDto pageDto) {
        Objects.requireNonNull(pageDto, "pageDto must not be null");
        Objects.requireNonNull(pageDto.getCurrent(), "current must not be null");
        Objects.requireNonNull(pageDto.getSize(), "size must not be null");
        return new Page<>(pageDto.getCurrent(), pageDto.getSize());
    }

    public static <T> PageVo<T> toPageVo(IPage<T> page) {
        Objects.requireNonNull(page, "page must not be null");
        List<T> records = Objects.requireNonNull(page.getRecords(), "page records must not be null");
        return new PageVo<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }

    public static <T, R> PageVo<R> toPageVo(IPage<T> page, Function<T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<R> records = Objects.requireNonNull(page, "page must not be null")
                .getRecords()
                .stream()
                .map(mapper)
                .toList();
        return new PageVo<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }
}
