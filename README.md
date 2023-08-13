# Query Publisher

Define your queries in SQL and publish them on the web via Pug templates.
All inside a simple Yaml file:

```yaml
port: 1960
pageHeader:
  content: |
    # Emp & Dept
    ![Emp & Dept](logo.png)
  
    This webapp allows you to peruse the time-honored `emp-dept` SQL schema.
  
    Enjoy!

routes:
  "depts":
    sql: |
      SELECT * FROM dept ORDER BY deptno
    template: |
      table
        caption
          span Departments
          hr
        tr
          th Code
          th Name
          th Location
        for row in rows
          tr
            td #{row.deptno}
            td #{row.dname}
            td #{row.loc}

database:
  className: 'org.h2.Driver'
  url: 'jdbc:h2:mem:test'
  user: sa
  password:
  initScript: |
    create table dept (
      deptno integer     not null primary key,
      dname  varchar(14) not null,
      loc    varchar(13) null
    );
  
    create table emp (
      empno    integer       not null  primary key,
      ename    varchar(10)   not null,
      job      varchar(9)    not null,
      mgr      integer                 references emp,
      hiredate date          not null,
      sal      numeric(7, 2) not null,
      comm     numeric(7, 2),
      deptno   integer       not null   references dept
    );
  
    insert into dept
      values (10, 'ACCOUNTING', 'NEW YORK'),
             (20, 'RESEARCH', 'DALLAS'),
             (30, 'SALES', 'CHICAGO'),
             (40, 'OPERATIONS', 'BOSTON');
  
    insert into emp
    values (7839, 'KING', 'PRESIDENT', null, '2011-11-17', 15000, null, 10),
           (7566, 'JONES', 'MANAGER', 7839, '2011-04-02', 14875, null, 20),
           (7788, 'SCOTT', 'ANALYST', 7566, '2012-12-09', 15000, null, 20),
           (7876, 'ADAMS', 'CLERK', 7788, '2013-01-12', 5500, null, 20),
           (7902, 'FORD', 'ANALYST', 7566, '2011-12-03', 15000, null, 20),
           (7369, 'SMITH', 'CLERK', 7902, '2010-12-17', 14250, null, 20),
           (7698, 'BLAKE', 'MANAGER', 7839, '2011-05-01', 14250, null, 30),
           (7499, 'ALLEN', 'SALESMAN', 7698, '2011-02-20', 8000, 1500, 30),
           (7521, 'WARD', 'SALESMAN', 7698, '2011-02-22', 6250, 2500, 30),
           (7654, 'MARTIN', 'SALESMAN', 7698, '2011-09-28', 6250, 7000, 30),
           (7844, 'TURNER', 'SALESMAN', 7698, '2011-09-08', 6000, 0, 30),
           (7900, 'JAMES', 'CLERK', 7698, '2011-12-03', 4750, null, 30),
           (7782, 'CLARK', 'MANAGER', 7839, '2011-06-09', 12250, null, 10),
           (7934, 'MILLER', 'CLERK', 7782, '2012-01-23', 6500, null, 10);
  
```
