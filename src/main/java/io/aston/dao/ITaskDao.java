package io.aston.dao;

import com.aston.micronaut.sql.aop.Query;
import com.aston.micronaut.sql.aop.SqlApi;
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
    Optional<Task> selectById(String id);

    @Query("""
            update ns_task set
            state=:state,
            output=:output,
            finished=:finished,
            retries=:retries
            where id=:id
            """)
    int updateState(TaskEntity task);

    @Query("""
            update ns_task set
            state=:state
            where id=:id
            """)
    void updateState(String id, State state);

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
}
