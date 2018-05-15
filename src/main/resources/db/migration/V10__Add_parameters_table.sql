create table parameters (
  image_name text not null,
  for_ratio text not null,
  revision integer not null default 1,
  width int,
  height int,
  crop_start_x int,
  crop_start_y int,
  crop_end_x int,
  crop_end_y int,
  focal_x int,
  focal_y int,
  ratio text,
  primary key (image_name, ratio)
);
