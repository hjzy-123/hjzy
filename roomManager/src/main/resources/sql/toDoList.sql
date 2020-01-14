-- 保存sql语句
create table record_info
(
	id          int       auto_increment,
	author      text      not null,
	content     text      not null,
	time        long      not null,
	constraint record_info
		primary key (id)
);

create table order_info_test4
(
    id      BIGINT default (NEXT VALUE FOR PUBLIC.SYSTEM_SEQUENCE_9E8862B4_92F6_403E_9303_078CE8948F05) auto_increment,
    order_number        BIGINT       not null,
    class_id        BIGINT       not null,
    course_id BIGINT             not null ,
    user_id BIGINT       not null,
    amount           decimal      not null,
    status      int not null default 0,
    create_time BIGINT not null ,
    comment_statue BIT not null default false,
    constraint order_info_test4
        primary key (id)
);

create table order_info_test7
(
    id      BIGINT default (NEXT VALUE FOR PUBLIC.SYSTEM_SEQUENCE_9E8862B4_92F6_403E_9303_078CE8948F05) auto_increment,
    order_number        BIGINT       not null,
    class_id        BIGINT       not null,
    course_id BIGINT             not null ,
    user_id BIGINT       not null,
    amount           decimal      not null,
    status      int not null default 0,
    create_time BIGINT not null ,
    comment_statue BIT not null default false,
    constraint order_info_test7
        primary key (id)
);

create table RECORD_INFO4
(
    ID      INTEGER default (NEXT VALUE FOR PUBLIC.SYSTEM_SEQUENCE_9E8862B4_92F6_403E_9303_078CE8948F05) auto_increment,
    AUTHOR  CLOB   not null,
    CONTENT CLOB   not null,
    TIME    BIGINT not null,
    LABEL   VARCHAR(50),
    constraint RECORD_INFO4
        primary key (ID)
);