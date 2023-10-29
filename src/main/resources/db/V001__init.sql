--drop table flyway_schema_history; drop table ns_task; drop table ns_workflow; drop table ns_workflow_def;

create table ns_workflow_def (
  name varchar(125) not null primary key,
  latest bool not null,
  created timestamp with time zone not null,
  data text not null
);

create table ns_task (
  id varchar(125) not null primary key,
  workflowId varchar(125) not null,
  ref integer not null,
  outputVar varchar(125),
  taskName varchar(125) not null,
  workflowName varchar(125) not null,
  params text,
  output text,
  state varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone,
  retries integer not null,
  taskDef text not null
);

create table ns_workflow (
  id varchar(125) not null primary key,
  uniqueCode varchar(125) not null,
  workflowName varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone,
  state varchar(125) not null,
  params text,
  scope text,
  defTasks text
);