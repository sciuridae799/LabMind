package com.labmind.common.web.database;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUtilTest {

    @Test
    void shouldConvertBasePageDtoToMybatisPage() {
        BasePageDto pageDto = new BasePageDto(2L, 20L);

        Page<String> page = PageUtil.toPage(pageDto);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(20L);
    }

    @Test
    void shouldConvertMybatisPageToPageVo() {
        Page<String> page = new Page<>(2L, 10L, 25L);
        page.setRecords(List.of("a", "b"));

        PageVo<String> pageVo = PageUtil.toPageVo(page);

        assertThat(pageVo.getCurrent()).isEqualTo(2L);
        assertThat(pageVo.getSize()).isEqualTo(10L);
        assertThat(pageVo.getTotal()).isEqualTo(25L);
        assertThat(pageVo.getPages()).isEqualTo(3L);
        assertThat(pageVo.getRecords()).containsExactly("a", "b");
    }

    @Test
    void shouldMapMybatisPageRecords() {
        Page<String> page = new Page<>(1L, 10L, 2L);
        page.setRecords(List.of("ab", "cd"));

        PageVo<Integer> pageVo = PageUtil.toPageVo(page, String::length);

        assertThat(pageVo.getRecords()).containsExactly(2, 2);
    }
}
