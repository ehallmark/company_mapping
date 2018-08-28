\connect companydb

drop table if exists revenues; -- old
drop table if exists segments_markets;
drop table if exists segments;
drop table if exists companies_markets;

-- model tables
drop table products;

create table products (
    id serial primary key,
    name text not null,
    revenue double precision,
    notes text,
    company_id integer,
    market_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create index products_company_id_idx on products (company_id);
create index products_market_id_idx on products (market_id);
create index products_name_idx on products (name);

drop table companies;
create table companies (
    id serial primary key,
    name text not null,
    revenue double precision,
    notes text,
    parent_company_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now()
);

create index companies_parent_company_id_idx on companies (parent_company_id);
create index companies_name_idx on companies (name);

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
