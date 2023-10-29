package io.aston;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aston.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AddWorkflow {

    public static void main(String[] args) {
        try {
            AddWorkflow add = new AddWorkflow();
            Workflow workflow = null;
            for (int i = 0; i < 2000; i++) {
                String workflowJson = add.post("http://localhost:8080/v1/workflows/", add.workflowCreate());
                System.out.println("create workflow " + i);
                System.out.println(workflowJson);
                workflow = add.objectMapper.readValue(workflowJson, Workflow.class);
            }

            String allTasks = add.get("http://localhost:8080/v1/tasks/");
            System.out.println("all tasks");
            System.out.println(allTasks);

            long t1 = System.currentTimeMillis();
            AtomicInteger total = new AtomicInteger();
            for (int k = 0; k < 10; k++) {
                new Thread(() -> {
                    for (int i = 0; i < 10000; i++) {
                        try {
                            workeer(add, t1, total.incrementAndGet());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }

            String w1Out = add.get("http://localhost:8080/v1/workflows/" + workflow.getId() + "?includeTasks=true");
            System.out.println("workflow");
            System.out.println(w1Out);

            String wAllOut = add.get("http://localhost:8080/v1/workflows/");
            System.out.println("all workflows");
            System.out.println(wAllOut);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void workeer(AddWorkflow add, long t1, int i) throws Exception {
        System.out.println("test queue " + i);

        String taskJson = add.get("http://localhost:8080/v1/runtime/queues?taskName=echo&workerId=local&timeout=30");
        if (taskJson != null && taskJson.startsWith("{")) {
            System.out.println("queue task " + i + " --- " + (System.currentTimeMillis() - t1));
            System.out.println(taskJson);
            Task task = add.objectMapper.readValue(taskJson, Task.class);
            TaskOutput output = new TaskOutput();
            output.setTaskId(task.getId());
            output.setOutput(task.getParams());
            if (task.getParams() != null) {
                for (Map.Entry<String, Object> e : task.getParams().entrySet()) {
                    if (e.getValue() instanceof Number n) {
                        e.setValue(n.intValue() + 1);
                        System.out.println("replace " + e.getKey() + " " + e.getValue());
                    }
                }
            }
            output.setState(State.COMPLETED);
            if (task.getRetries() < 2) {
                output.setOutput(null);
                output.setState(State.FAILED);
            }
            String respOut = add.put("http://localhost:8080/v1/runtime/tasks/" + task.getId(), output);
            System.out.println("queue task save " + i);
            System.out.println(add.objectMapper.writeValueAsString(output));
            System.out.println(respOut);
        }
    }

    private String post(String path, Object data) throws Exception {
        HttpRequest r1 = HttpRequest.newBuilder()
                .uri(new URI(path))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(data)))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = cl.send(r1, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.out.println("error: " + resp.statusCode());
            System.out.println(resp.body());
            return null;
        }
        return resp.body();
    }

    private String put(String path, Object data) throws Exception {
        HttpRequest r1 = HttpRequest.newBuilder()
                .uri(new URI(path))
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(data)))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = cl.send(r1, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.out.println("error: " + resp.statusCode());
            System.out.println(resp.body());
            return null;
        }
        return resp.body();
    }

    private String get(String path) throws Exception {
        HttpRequest r1 = HttpRequest.newBuilder()
                .uri(new URI(path))
                .GET().build();
        HttpResponse<String> resp = cl.send(r1, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.out.println("error: " + resp.statusCode());
            System.out.println(resp.body());
            return null;
        }
        return resp.body();
    }

    HttpClient cl;
    ObjectMapper objectMapper;

    public AddWorkflow() {
        this.cl = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    }

    public WorkflowCreate workflowCreate() {
        WorkflowCreate cr = new WorkflowCreate();
        cr.setParams(new HashMap<>());
        cr.getParams().put("a", "a");
        cr.getParams().put("b", 2);
        cr.setName("test");
        cr.setUniqueCode("test-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        cr.setTasks(new ArrayList<>());
        cr.getTasks().add(taskDef());
        cr.getTasks().add(taskDef());
        cr.getTasks().add(taskDef());
        cr.getTasks().add(taskDef());
        return cr;
    }

    public TaskDef taskDef() {
        TaskDef def = new TaskDef();
        def.setName("echo");
        def.setParams(new HashMap<>());
        def.getParams().put("a", "${a}");
        def.getParams().put("b", "${b}");
        def.setOutputVar("$.");
        def.setTimeout(10_000);
        def.setRetryCount(4);
        def.setRetryWait(3_000);
        return def;
    }
}
