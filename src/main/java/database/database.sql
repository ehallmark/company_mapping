\connect companydb

drop table if exists segments_markets;
drop table if exists segments;

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


drop table market_revenues;
create table market_revenues (
    id serial primary key,
    value double precision not null,
    year integer not null,
    notes text,
    source text,
    is_percentage boolean not null default ('f'),
    is_estimate boolean not null default ('f'),
    estimate_type integer check (estimate_type in (0, 1, 2)),
    cagr double precision,
    market_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now(),
    check (notes is not null OR source is not null),
    check (is_estimate OR (source is not null)),
    check ((not is_estimate) OR estimate_type is not null)
);

create index market_revenues_market_id_idx on market_revenues (market_id);

drop table company_revenues;
create table company_revenues (
    id serial primary key,
    value double precision not null,
    year integer not null,
    notes text,
    source text,
    is_percentage boolean,
    is_estimate boolean,
    estimate_type integer check (estimate_type is null or estimate_type in (0, 1, 2)),
    cagr double precision,
    company_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now(),
    check (notes is not null OR source is not null),
    check (is_estimate OR (source is not null)),
    check ((not is_estimate) OR estimate_type is not null)
);

create index company_revenues_company_id_idx on company_revenues (company_id);


drop table product_revenues;
create table product_revenues (
    id serial primary key,
    value double precision not null,
    year integer not null,
    notes text,
    source text,
    is_percentage boolean,
    is_estimate boolean,
    estimate_type integer check (estimate_type is null or estimate_type in (0, 1, 2)),
    cagr double precision,
    product_id integer,
    updated_at timestamp not null default now(),
    created_at timestamp not null default now(),
    check (notes is not null OR source is not null),
    check (is_estimate OR (source is not null)),
    check ((not is_estimate) OR estimate_type is not null)
);

create index product_revenues_product_id_idx on product_revenues (product_id);


drop table companies_markets;
create table companies_markets (
    company_id integer not null,
    market_id integer not null,
    primary key (company_id, market_id)
);

