\connect companydb

drop table revenues; -- old
drop table companies_markets;

-- model tables
drop table products;

create table products (
    id serial primary key,
    name text not null,
    revenue double precision,
    notes text,
    segment_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create index products_segment_id_idx on products (segment_id);
create index products_name_idx on products (name);

drop table segments;

create table segments (
    id serial primary key,
    name text not null,
    revenue double precision,
    notes text,
    parent_segment_id integer,
    company_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create index segments_parent_segment_id_idx on segments (parent_segment_id);
create index segments_company_id_idx on segments (company_id);
create index segments_name_idx on segments (name);


drop table markets;

create table markets (
    id serial primary key,
    name text not null,
    revenue double precision,
    parent_market_id integer,
    notes text,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create index markets_name_idx on markets (name);
create index markets_parent_market_id_idx on markets (parent_market_id);


drop table companies;

create table companies (
    id serial primary key,
    name text not null,
    revenue double precision,
    notes text,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create index companies_name_idx on companies (name);


-- join tables

drop table segments_markets;

create table segments_markets (
    segment_id integer not null,
    market_id integer not null,
    primary key(segment_id, market_id)
);


