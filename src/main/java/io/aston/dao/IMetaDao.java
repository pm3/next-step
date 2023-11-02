package io.aston.dao;

import com.aston.micronaut.sql.aop.Query;
import com.aston.micronaut.sql.aop.SqlApi;
import io.aston.entity.MetaTemplateEntity;
import io.aston.entity.MetaWfEntity;

import java.util.List;
import java.util.Optional;

@SqlApi
public interface IMetaDao {
    void insert(MetaTemplateEntity workflowDef);

    void update(MetaTemplateEntity workflowDef);

    @Query("select * from ns_meta_template where name=:name")
    Optional<MetaTemplateEntity> loadTemplateByName(String name);

    @Query("""
            select *
            from ns_meta_template
            where latest=true
            and name like :name || '/%'
            order by created asc
            limit 1
            """)
    Optional<MetaTemplateEntity> searchLatestByName(String name);

    @Query("""
            select *
            from ns_meta_template
            where 1=1
            /** and latest = :latest */
            /** and name like :name || '/%' */
            order by created desc
            """)
    List<MetaTemplateEntity> search(Boolean latest, String name);

    void insert(MetaWfEntity metaWfEntity);

    void update(MetaWfEntity metaWfEntity);

    @Query("delete from ns_meta_wf where workflowId=:workflowId")
    void deleteMetaWf(String workflowId);

    @Query("select * from ns_meta_wf where workflowId=:workflowId")
    Optional<MetaWfEntity> loadMetaWfById(String workflowId);
}
