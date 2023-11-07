package io.aston.utils;

import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.entity.CronTemplateEntity;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.micronaut.core.annotation.Nullable;

import java.lang.reflect.Field;
import java.time.Instant;

public class GenSql {
    public static void main(String[] args) {
        GenSql genSql = new GenSql();
        StringBuilder sb = new StringBuilder();
        genSql.genClass(TaskEntity.class, sb);
        genSql.genClass(WorkflowEntity.class, sb);
        genSql.genClass(CronTemplateEntity.class, sb);
        System.out.println(sb);
    }

    public void genClass(Class<?> cl, StringBuilder sb) {
        Table t = cl.getAnnotation(Table.class);
        String tname = t != null ? t.name() : cl.getName();
        sb.append("create table ").append(tname).append(" (\n");
        int pos = 0;
        for (Field f : cl.getDeclaredFields()) {
            pos++;
            Class<?> ft = f.getType();
            Format format = f.getAnnotation(Format.class);
            String stype = "varchar(125)";
            boolean notNull = f.getAnnotation(Nullable.class) == null;
            if (ft.equals(int.class) || ft.equals(Integer.class)) stype = "integer";
            if (ft.equals(long.class) || ft.equals(Long.class)) stype = "bigint";
            if (ft.equals(double.class)) stype = "decimal(5,4)";
            if (ft.equals(Double.class)) stype = "decimal(5,4)";
            if (ft.equals(Instant.class)) stype = "timestamp with time zone";
            if (ft.equals(boolean.class) || ft.equals(Boolean.class)) stype = "bool";
            if (f.getName().equals("id") && Number.class.isAssignableFrom(ft)) stype = "serial primary key";
            if (f.getName().equals("created")) notNull = true;
            if (format != null && format.value().equals("json")) stype = "text";
            if (ft.isPrimitive()) notNull = true;
            if (ft.isEnum()) notNull = true;
            sb.append("  ").append(f.getName()).append(" ").append(stype).append(notNull ? " not null" : "").append(pos == 1 ? " primary key" : "").append(",\n");
        }
        sb.setLength(sb.length() - 2);
        sb.append("\n);\n\n");
    }
}
