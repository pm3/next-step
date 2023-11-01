package io.aston.dao;

import com.aston.micronaut.sql.aop.Query;
import com.aston.micronaut.sql.aop.SqlApi;
import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.where.Multi;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.Workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SqlApi
public interface IWorkflowDao {
    void insert(WorkflowEntity workflow);

    void update(WorkflowEntity workflow);

    Optional<WorkflowEntity> loadById(String id);

    @Query("""
            update ns_workflow set
            state=:state, modified=:modified
            where id=:id
            """)
    void updateState(WorkflowEntity workflow);

    @Query("""
            update ns_workflow set
            scope = scope::jsonb || :scope::jsonb
            where id=:id
            """)
    void updateScope(String id, @Format(JsonConverterFactory.JSON) Map<String, Object> scope);

    @Query("update ns_workflow set state=:state, worker=:worker, modified=:modified where id=:id")
    void updateWorker(String id, State state, String worker, Instant modified);

    @Query("select * from ns_workflow where id=:id")
    Optional<Workflow> selectById(String id);

    @Query("""
            select *
            from ns_workflow
            where 1=1
            /** and workflowName in (:workflowNames) */
            /** and state in (:states) */
            /** and created >= :dateFrom */
            /** and created < :dateTo */
            order by created desc
            """)
    List<Workflow> selectByQuery(Multi<String> workflowNames,
                                 Multi<String> states,
                                 Instant dateFrom,
                                 Instant dateTo);
}
