--drop table flyway_schema_history; drop table ns_task; drop table ns_workflow; drop table ns_cron_template;

create table ns_task (
  id varchar(125) not null primary key,
  workflowId varchar(125) not null,
  workerId varchar(125),
  taskName varchar(125) not null,
  workflowName varchar(125) not null,
  params text,
  output text,
  state varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone,
  retries integer not null,
  runningTimeout integer not null,
  maxRetryCount integer not null,
  retryWait integer not null
);

create table ns_workflow (
  id varchar(125) not null primary key,
  uniqueCode varchar(125) not null,
  workflowName varchar(125) not null,
  state varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone,
  params text,
  output text,
  workerId varchar(125)
);

create table ns_cron_template (
  name varchar(125) not null primary key,
  description varchar(125) not null,
  uniqueCodeExpr varchar(125) not null,
  cronExpressions varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone not null
);