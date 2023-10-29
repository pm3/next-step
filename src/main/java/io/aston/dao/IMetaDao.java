package io.aston.dao;

import com.aston.micronaut.sql.aop.Query;
import com.aston.micronaut.sql.aop.SqlApi;
import io.aston.entity.WorkflowDefEntity;

import java.util.List;
import java.util.Optional;

@SqlApi
public interface IMetaDao {
    void insert(WorkflowDefEntity workflowDef);

    void update(WorkflowDefEntity workflowDef);

    Optional<WorkflowDefEntity> loadById(String name);

    @Query("""
            select *
            from ns_workflow_def
            where latest=true
            and name like :name || '/%'
            order by created asc
            limit 1
            """)
    Optional<WorkflowDefEntity> searchLatestByName(String name);

    @Query("""
            select *
            from ns_workflow_def
            where 1=1
            /** and latest = :latest */
            /** and name like :name || '/%' */
            order by created desc
            """)
    List<WorkflowDefEntity> search(Boolean latest, String name);
}
