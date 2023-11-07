package io.aston.controller;

import io.aston.api.MetaWorkflowApi;
import io.aston.dao.IMetaDao;
import io.aston.entity.CronTemplateEntity;
import io.aston.model.WorkflowTemplate;
import io.aston.user.UserDataException;
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
    public void create(WorkflowTemplate workflowDef) {
        CronTemplateEntity workflowDefEntity = new CronTemplateEntity();
        workflowDefEntity.setName(workflowDefEntity.getName());
        workflowDefEntity.setUniqueCodeExpr(workflowDefEntity.getUniqueCodeExpr());
        workflowDefEntity.setCronExpressions(workflowDef.getCronExpressions());
        workflowDefEntity.setCreated(Instant.now());
        workflowDefEntity.setModified(workflowDefEntity.getCreated());
        metaDao.insert(workflowDefEntity);
    }

    @Override
    public List<WorkflowTemplate> selectAll() {
        return metaDao.selectAll();
    }

    @Override
    public WorkflowTemplate fetch(String name) {
        return metaDao.loadByName(name)
                .orElseThrow(() -> new UserDataException("not found"));
    }

    @Override
    public WorkflowTemplate delete(String name) {
        throw new UserDataException("not supported");
    }
}
