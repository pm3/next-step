package io.aston.service;

import io.aston.dao.IMetaDao;
import io.aston.entity.MetaTemplateEntity;
import io.aston.entity.MetaWfEntity;
import io.aston.model.TaskDef;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MetaCacheService {
    private final IMetaDao metaDao;
    private final Map<String, List<TaskDef>> metaWfCache = new ConcurrentHashMap<>();

    public MetaCacheService(IMetaDao metaDao) {
        this.metaDao = metaDao;
    }

    public Optional<MetaTemplateEntity> loadTemplateByName(String name) {
        return metaDao.loadTemplateByName(name);
    }

    public Optional<MetaTemplateEntity> searchLatestByName(String name) {
        return metaDao.searchLatestByName(name);
    }

    public List<TaskDef> workflowTasks(String workflowId) {
        List<TaskDef> l = metaWfCache.get(workflowId);
        if (l == null) {
            l = metaDao.loadMetaWfById(workflowId)
                    .map(MetaWfEntity::getTasks).orElse(null);
        }
        return l;
    }

    public Map<Integer, TaskDef> workflowTaskMap(String workflowId) {
        Map<Integer, TaskDef> defMap = new HashMap<>();
        List<TaskDef> l = workflowTasks(workflowId);
        if (l != null) {
            for (TaskDef def : l) defMap.put(def.getRef(), def);
        }
        return defMap;
    }

    public void saveWorkflowTasks(String workflowId, List<TaskDef> tasks) {
        metaWfCache.put(workflowId, tasks);
        MetaWfEntity metaWfEntity = new MetaWfEntity();
        metaWfEntity.setWorkflowId(workflowId);
        metaWfEntity.setCreated(Instant.now());
        metaWfEntity.setTasks(tasks);
        metaDao.insert(metaWfEntity);
    }

    public void deleteWorkflowTasks(String workflowId) {
        metaWfCache.remove(workflowId);
        metaDao.deleteMetaWf(workflowId);
    }
}
