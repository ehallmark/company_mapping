\connect companydb

-- model tables

create table products (
    id serial primary key,
    name text,
    notes text,
    company_id integer,
    market_id integer,
    segment_id integer,
    updated_at timestamp,
    created_at timestamp
);

create index products_company_id_idx on products (company_id);
create index products_segment_id_idx on products (segment_id);
create index products_market_id_idx on products (market_id);
create index products_name_idx on products (name);

create table segments (
    id serial primary key,
    name text,
    notes text,
    parent_segment_id integer,
    company_id integer,
    updated_at timestamp,
    created_at timestamp
);

create index segments_parent_segment_id_idx on segments (parent_segment_id);
create index segments_company_id_idx on segments (company_id);
create index segments_name_idx on segments (name);


create table revenues (
    id serial primary key,
    value double precision,
    product_id integer,
    segment_id integer,
    market_id integer,
    company_id integer,
    is_estimate boolean,
    is_percentage boolean,
    notes text,
    updated_at timestamp,
    created_at timestamp
);

create index revenues_product_id_idx on revenues (product_id);
create index revenues_market_id_idx on revenues (market_id);
create index revenues_segment_id_idx on revenues (segment_id);
create index revenues_company_id_idx on revenues (company_id);
create index revenues_name_idx on revenues (name);


create table markets (
    id serial primary key,
    name text,
    parent_market_id integer,
    notes text,
    updated_at timestamp,
    created_at timestamp
);

create index markets_name_idx on markets (name);
create index markets_parent_market_id_idx on markets (parent_market_id);


create table companies (
    id serial primary key,
    name text,
    notes text,
    updated_at timestamp,
    created_at timestamp
);

create index companies_name_idx on companies (name);


-- join tables

create table companies_markets (
    company_id integer not null,
    market_id integer not null,
    primary key(company_id, market_id)
);

create table segments_markets (
    segment_id integer not null,
    market_id integer not null,
    primary key(company_id, market_id)
);


