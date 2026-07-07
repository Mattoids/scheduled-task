package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskDependency;
import com.mattoid.scheduled.mapper.TaskDependencyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Slf4j
@Service
public class TaskDependencyService extends ServiceImpl<TaskDependencyMapper, TaskDependency> {

    @Transactional(rollbackFor = Exception.class)
    public void saveDependencies(Long taskId, List<Long> dependsOnTaskIds) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }
        lambdaUpdate().eq(TaskDependency::getTaskId, taskId).remove();
        if (CollectionUtils.isEmpty(dependsOnTaskIds)) {
            return;
        }
        Set<Long> uniqueDeps = new LinkedHashSet<>(dependsOnTaskIds);
        uniqueDeps.remove(taskId);
        if (uniqueDeps.isEmpty()) {
            return;
        }

        List<TaskDependency> relations = uniqueDeps.stream()
                .map(depId -> {
                    TaskDependency relation = new TaskDependency();
                    relation.setTaskId(taskId);
                    relation.setDependsOnTaskId(depId);
                    return relation;
                })
                .toList();
        saveBatch(relations);

        if (hasCycle(taskId)) {
            throw new IllegalArgumentException("保存依赖失败：存在循环依赖");
        }
    }

    public List<Long> getDependencyIds(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(TaskDependency::getTaskId, taskId)
                .list()
                .stream()
                .map(TaskDependency::getDependsOnTaskId)
                .toList();
    }

    public List<Long> getDependentIds(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(TaskDependency::getDependsOnTaskId, taskId)
                .list()
                .stream()
                .map(TaskDependency::getTaskId)
                .toList();
    }

    /**
     * 获取 taskId 的所有上游依赖（包含间接依赖），按拓扑顺序排列。
     */
    public List<Long> getAllDependencies(Long taskId) {
        List<Long> sorted = topologicalSort(taskId);
        sorted.remove(taskId);
        return sorted;
    }

    /**
     * 对 taskId 及其所有上游依赖进行拓扑排序，taskId 排在最后。
     */
    public List<Long> topologicalSort(Long taskId) {
        Map<Long, Set<Long>> graph = new HashMap<>();
        collectGraph(taskId, graph, new HashSet<>());

        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> visiting = new HashSet<>();

        for (Long node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfsTopological(node, graph, visiting, visited, result);
            }
        }
        return result;
    }

    private void collectGraph(Long taskId, Map<Long, Set<Long>> graph, Set<Long> collected) {
        if (taskId == null || collected.contains(taskId)) {
            return;
        }
        collected.add(taskId);
        graph.putIfAbsent(taskId, new HashSet<>());
        List<Long> deps = getDependencyIds(taskId);
        for (Long dep : deps) {
            graph.get(taskId).add(dep);
            graph.putIfAbsent(dep, new HashSet<>());
            collectGraph(dep, graph, collected);
        }
    }

    private void dfsTopological(Long node, Map<Long, Set<Long>> graph,
                                Set<Long> visiting, Set<Long> visited, List<Long> result) {
        if (visiting.contains(node)) {
            throw new IllegalStateException("检测到循环依赖，任务 ID: " + node);
        }
        if (visited.contains(node)) {
            return;
        }
        visiting.add(node);
        for (Long dep : graph.getOrDefault(node, Collections.emptySet())) {
            dfsTopological(dep, graph, visiting, visited, result);
        }
        visiting.remove(node);
        visited.add(node);
        result.add(node);
    }

    public boolean hasCycle(Long taskId) {
        try {
            topologicalSort(taskId);
            return false;
        } catch (IllegalStateException e) {
            log.warn("任务 {} 存在循环依赖", taskId);
            return true;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeByTaskId(Long taskId) {
        lambdaUpdate().eq(TaskDependency::getTaskId, taskId).remove();
        lambdaUpdate().eq(TaskDependency::getDependsOnTaskId, taskId).remove();
    }
}
