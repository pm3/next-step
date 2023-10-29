package io.aston.controller;

import io.aston.api.MetaWorkflowApi;
import io.aston.dao.IMetaDao;
import io.aston.entity.WorkflowDefEntity;
import io.aston.model.WorkflowDef;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;

import java.time.Instant;
import java.util.List;

@Controller("/v1")
public class MetaWorkflowController implements MetaWorkflowApi {

    private final IMetaDao metaDao;

    public MetaWorkflowController(IMetaDao metaDao) {
        this.metaDao = metaDao;
    }

    @Override
    public WorkflowDef create(WorkflowDef workflowDef) {
        WorkflowDefEntity last = metaDao.searchLatestByName(workflowDef.getName()).orElse(null);
        String name = workflowDef.getName() + "/1";
        if (last != null) {
            int lastVersion = Integer.parseInt(last.getName().split("/")[1]);
            name = workflowDef.getName() + "/" + (lastVersion + 1);
            last.setLatest(false);
            last.getData().setLatest(false);
            metaDao.update(last);
        }
        WorkflowDefEntity workflowDefEntity = new WorkflowDefEntity();
        workflowDefEntity.setName(name);
        workflowDefEntity.setLatest(true);
        workflowDefEntity.setCreated(Instant.now());
        workflowDefEntity.setData(workflowDef);
        workflowDef.setName(name);
        workflowDef.setLatest(true);
        workflowDef.setCreated(workflowDefEntity.getCreated());
        metaDao.insert(workflowDefEntity);
        return workflowDef;
    }

    @Override
    public List<WorkflowDef> search(@Nullable Boolean latest, @Nullable String name) {
        return metaDao.search(latest, name)
                .stream().map(WorkflowDefEntity::getData).toList();
    }

    @Override
    public WorkflowDef fetch(String name) {
        return metaDao.searchLatestByName(name)
                .map(WorkflowDefEntity::getData)
                .orElseThrow(() -> new UserDataException("not found"));
    }

    @Override
    public WorkflowDef delete(String name) {
        throw new UserDataException("not supported");
    }
}
