--drop table flyway_schema_history; drop table ns_task; drop table ns_workflow; drop table ns_meta_template; drop table ns_meta_wf;

create table ns_task (
  id varchar(125) not null primary key,
  workflowId varchar(125) not null,
  workerId varchar(125),
  ref integer not null,
  taskName varchar(125) not null,
  workflowName varchar(125) not null,
  params text,
  output text,
  state varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone,
  retries integer not null,
  runningTimeout bigint not null,
  maxRetryCount integer not null,
  retryWait bigint not null
);

create table ns_workflow (
  id varchar(125) not null primary key,
  uniqueCode varchar(125) not null,
  workflowName varchar(125) not null,
  created timestamp with time zone not null,
  modified timestamp with time zone,
  state varchar(125) not null,
  params text,
  output text,
  workerId varchar(125)
);

create table ns_meta_template (
  name varchar(125) not null primary key,
  latest bool not null,
  created timestamp with time zone not null,
  data text not null
);

create table ns_meta_wf (
  workflowId varchar(125) not null primary key,
  created timestamp with time zone not null,
  tasks text not null
);