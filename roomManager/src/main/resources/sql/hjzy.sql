-- 保存sql语句
create table user_info
(
    id       bigint auto_increment
        primary key,
    userName varchar(255) default 'default.png' not null,
    email    varchar(255)                       not null,
    password varchar(255)                       not null,
    constraint user_info_userName_uindex
        unique (userName)
);


create table video_info
(
    id         bigint auto_increment
        primary key,
    user_name  varchar(255) not null,
    video_name varchar(255) not null,
    comments   text         not null,
    constraint video_info_user_info_userName_fk
        foreign key (user_name) references user_info (userName)
            on update cascade on delete cascade
);
