package com.superagent.common.web.database;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusAutoConfigurationTest {

    @Test
    void shouldCreatePaginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusAutoConfiguration().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .singleElement()
                .isInstanceOf(PaginationInnerInterceptor.class);
    }

    @Test
    void shouldAutoFillCreateAndEditTime() {
        MetaObjectHandler handler = new MybatisPlusAutoConfiguration().mybatisPlusMetaObjectHandler();
        TestTableData entity = new TestTableData();
        MetaObject metaObject = createMetaObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getEditTime()).isNotNull();
    }

    @Test
    void shouldUpdateEditTimeOnly() {
        MetaObjectHandler handler = new MybatisPlusAutoConfiguration().mybatisPlusMetaObjectHandler();
        TestTableData entity = new TestTableData();
        LocalDateTime createTime = LocalDateTime.of(2026, 4, 20, 10, 20, 30);
        LocalDateTime editTime = LocalDateTime.of(2026, 4, 20, 10, 20, 31);
        entity.setCreateTime(createTime);
        entity.setEditTime(editTime);
        MetaObject metaObject = createMetaObject(entity);

        handler.updateFill(metaObject);

        assertThat(entity.getCreateTime()).isEqualTo(createTime);
        assertThat(entity.getEditTime()).isAfter(editTime);
    }

    private MetaObject createMetaObject(TestTableData entity) {
        TableInfoHelper.remove(TestTableData.class);
        MapperBuilderAssistant mapperBuilderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        mapperBuilderAssistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(mapperBuilderAssistant, TestTableData.class);
        return SystemMetaObject.forObject(entity);
    }

    static class TestTableData extends BaseTableData {

        @TableId
        private Long id;
    }
}
