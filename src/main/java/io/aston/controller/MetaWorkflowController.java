package io.aston.controller;

import io.aston.api.MetaWorkflowApi;
import io.aston.dao.IMetaDao;
import io.aston.entity.MetaTemplateEntity;
import io.aston.model.TaskDef;
import io.aston.model.WorkflowTemplate;
import io.aston.service.MetaCacheService;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;

import java.time.Instant;
import java.util.List;

@Controller("/v1")
public class MetaWorkflowController implements MetaWorkflowApi {

    private final IMetaDao metaDao;
    private final MetaCacheService metaCacheService;

    public MetaWorkflowController(IMetaDao metaDao, MetaCacheService metaCacheService) {
        this.metaDao = metaDao;
        this.metaCacheService = metaCacheService;
    }

    @Override
    public WorkflowTemplate create(WorkflowTemplate workflowDef) {
        MetaTemplateEntity last = metaDao.searchLatestByName(workflowDef.getName()).orElse(null);
        String name = workflowDef.getName() + "/1";
        if (last != null) {
            int lastVersion = Integer.parseInt(last.getName().split("/")[1]);
            name = workflowDef.getName() + "/" + (lastVersion + 1);
            last.setLatest(false);
            last.getData().setLatest(false);
            metaDao.update(last);
        }
        MetaTemplateEntity workflowDefEntity = new MetaTemplateEntity();
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
    public List<WorkflowTemplate> search(@Nullable Boolean latest, @Nullable String name) {
        return metaDao.search(latest, name)
                .stream().map(MetaTemplateEntity::getData).toList();
    }

    @Override
    public WorkflowTemplate fetch(String name) {
        return metaDao.searchLatestByName(name)
                .map(MetaTemplateEntity::getData)
                .orElseThrow(() -> new UserDataException("not found"));
    }

    @Override
    public WorkflowTemplate delete(String name) {
        throw new UserDataException("not supported");
    }

    @Override
    public List<TaskDef> fetchWorkflowTasks(String id) {
        return metaCacheService.workflowTasks(id);
    }
}
