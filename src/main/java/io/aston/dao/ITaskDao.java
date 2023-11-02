package io.aston.dao;

import com.aston.micronaut.sql.aop.Query;
import com.aston.micronaut.sql.aop.SqlApi;
import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.where.Multi;
import io.aston.entity.TaskEntity;
import io.aston.model.State;
import io.aston.model.Task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SqlApi
public interface ITaskDao {
    void insert(TaskEntity task);

    void update(TaskEntity task);

    Optional<TaskEntity> loadById(String id);

    @Query("select * from ns_task where id=:id")
    Optional<Task> loadTaskById(String id);

    @Query("""
            update ns_task set
            state=:state,
            output=:output,
            retries=:retries,
            modified=:modified,
            workerId=:workerId
            where id=:id
            """)
    void updateState(TaskEntity task);

    @Query("""
            update ns_task set
            state=:newState,
            output=:output,
            modified=:modified
            where id=:id and state=:oldState
            """)
    int updateState(String id, State oldState, State newState, @Format(JsonConverterFactory.JSON) Object output, Instant modified);

    @Query("""
            update ns_task set
            state=:newState,
            output=null,
            retries=retries+1
            modified=:modified
            where id=:id and state=:oldState and retries<maxRetryCount
            """)
    int updateStateAndRetry(String id, State oldState, State newState, Instant modified);

    @Query("""
            update ns_task set
            state=:newState,
            output=:output,
            modified=:modified
            where workflowId=:workflowId and state in(:oldStates)
            """)
    void updateWorkflowAll(String workflowId,
                           Multi<State> oldStates,
                           State newState,
                           @Format(JsonConverterFactory.JSON) Object output,
                           Instant modified);

    @Query("""
            select *
            from ns_task
            where workflowId=:workflowId
            order by created
            """)
    List<Task> selectByWorkflow(String workflowId);

    @Query("""
            select *
            from ns_task
            where 1=1
            /** and name in (:names) */
            /** and state in (:states) */
            /** and workflowName in (:workflowNames) */
            /** and created >= :dateFrom */
            /** and created < :dateTo */
            order by created
            """)
    List<Task> selectByQuery(Multi<String> names,
                             Multi<String> states,
                             Multi<String> workflowNames,
                             Instant dateFrom,
                             Instant dateTo);

    @Query("""
            select *
            from ns_task
            where workflowId=:workflowId
            and state='COMPLETED'
            and output is not null
            order by created
            """)
    List<TaskEntity> searchWorkflowScopeTasks(String workflowId);
}
