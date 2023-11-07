package io.aston.dao;

import com.aston.micronaut.sql.aop.Query;
import com.aston.micronaut.sql.aop.SqlApi;
import io.aston.entity.CronTemplateEntity;
import io.aston.model.WorkflowTemplate;

import java.util.List;
import java.util.Optional;

@SqlApi
public interface IMetaDao {
    void insert(CronTemplateEntity cronTemplate);

    void update(CronTemplateEntity cronTemplate);

    Optional<CronTemplateEntity> loadById(String name);

    Optional<WorkflowTemplate> loadByName(String name);

    @Query("""
            select *
            from ns_meta_template
            order by created desc
            """)
    List<WorkflowTemplate> selectAll();
}
