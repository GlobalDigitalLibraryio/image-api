drop table parameters;
create table image_variants (
  image_url text not null,
  ratio text not null,
  revision integer not null default 1,
  top_left_x int,
  top_left_y int,
  width int,
  height int,
  primary key (image_url, ratio)
);
